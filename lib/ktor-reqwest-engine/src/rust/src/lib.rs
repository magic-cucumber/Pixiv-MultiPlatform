mod jvm;

use futures::future::select_ok;
use hyper::Uri;
use hyper::client::connect::{Connected, Connection};
use hyper::service::Service;
use openssl::ssl::{SslConnector, SslMethod, SslVerifyMode};
use std::future::Future;
use std::io;
use std::pin::Pin;
use std::sync::Arc;
use std::task::{Context, Poll};
use tokio::io::{AsyncRead, AsyncWrite};
use tokio::net::{TcpStream, lookup_host};
use tokio::time::{Duration, timeout};
use tokio_openssl::SslStream;

struct SslConnection(SslStream<TcpStream>);

impl Connection for SslConnection {
    fn connected(&self) -> Connected {
        Connected::new()
    }
}

impl AsyncRead for SslConnection {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut tokio::io::ReadBuf<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_read(cx, buf)
    }
}

impl AsyncWrite for SslConnection {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &[u8]) -> Poll<io::Result<usize>> {
        Pin::new(&mut self.0).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_shutdown(cx)
    }
}

// Tokio Executor 实现
#[derive(Clone)]
struct TokioExecutor;

impl<F> hyper::rt::Executor<F> for TokioExecutor
where
    F: Future<Output = ()> + Send + 'static,
{
    fn execute(&self, fut: F) {
        tokio::spawn(fut);
    }
}

// DNS解析器类型别名
type DnsResolver = Arc<dyn Fn(&str) -> Pin<Box<dyn Future<Output = Result<Vec<String>, Box<dyn std::error::Error + Send + Sync>>> + Send>> + Send + Sync>;
fn system() -> DnsResolver {
    Arc::new(|hostname: &str| {
        let hostname = hostname.to_string();
        Box::pin(async move {
            // 调用系统DNS解析（使用任意端口，因为我们只需要IP地址）
            let addr = format!("{}:0", hostname);
            match lookup_host(&addr).await {
                Ok(addrs) => {
                    let ips: Vec<String> = addrs.map(|addr| addr.ip().to_string()).collect();
                    if ips.is_empty() { Err("No IP addresses found".into()) } else { Ok(ips) }
                }
                Err(e) => Err(Box::new(e) as Box<dyn std::error::Error + Send + Sync>),
            }
        })
    })
}

// 自定义 Connector Service
#[derive(Clone)]
struct KeqwestConnector {
    dns: DnsResolver,
    ssl_connector: SslConnector,
    ignore_ssl_error: bool,
}

impl KeqwestConnector {
    fn new(dns_resolver: Option<DnsResolver>, ignore_ssl_error: bool) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let dns_resolver = dns_resolver.unwrap_or_else(|| system());

        // 创建 SSL Connector
        let mut builder = SslConnector::builder(SslMethod::tls()).map_err(|e| format!("create ssl builder failed: {}", e))?;
        if ignore_ssl_error {
            builder.set_verify(SslVerifyMode::NONE);
        }
        let ssl_connector = builder.build();

        Ok(Self { dns: dns_resolver, ssl_connector, ignore_ssl_error })
    }
}

impl Service<Uri> for KeqwestConnector {
    type Response = SslConnection;
    type Error = Box<dyn std::error::Error + Send + Sync>;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, uri: Uri) -> Self::Future {
        let dns_resolver = self.dns.clone();
        let ssl_connector = self.ssl_connector.clone();
        let ignore_ssl_error = self.ignore_ssl_error.clone();

        Box::pin(async move {
            // 从 URI 中提取主机名和端口
            let hostname = uri.host().ok_or("No hostname in URI")?.to_string();
            let port = uri.port_u16().unwrap_or(443);

            eprintln!("[CustomConnector] Resolving DNS for hostname: {}", hostname);
            // 调用DNS解析器获取IP列表，添加5秒超时
            let candidates = match dns_resolver(&hostname).await {
                //never timeout,the timeout will place in application
                Ok(dns_ips) => {
                    eprintln!("[CustomConnector] DNS resolved successfully: {:?}", dns_ips);
                    dns_ips
                }
                Err(e) => {
                    eprintln!("[CustomConnector] DNS resolution failed: {:?}", e);
                    return Err(format!("DNS resolution failed: {:?}", e).into());
                }
            };

            if candidates.is_empty() {
                eprintln!("[CustomConnector] DNS returned empty list");
                return Err("DNS returned empty list".into());
            }

            let debug_ips: Vec<&str> = candidates.iter().map(|entry| entry.as_str()).collect();
            eprintln!("[CustomConnector] Final IP list: {:?}", debug_ips);

            let connection_futures: Vec<_> = candidates
                .into_iter()
                .map(|candidate| {
                    let addr = format!("{}:{}", candidate, port);
                    let ssl_connector_clone = ssl_connector.clone();
                    let hostname = hostname.clone();

                    Box::pin(async move {
                        eprintln!("[CustomConnector] Attempting to connect to: {}", addr);

                        // TCP 连接超时（5秒）
                        let tcp_result = timeout(Duration::from_secs(5), TcpStream::connect(&addr)).await;

                        let tcp = match tcp_result {
                            Ok(Ok(tcp)) => {
                                eprintln!("[CustomConnector] TCP connection established to {}", addr);
                                tcp
                            }
                            Ok(Err(e)) => {
                                eprintln!("[CustomConnector] TCP connect failed to {}: {}", addr, e);
                                return Err(format!("TCP connect to {} failed: {}", addr, e));
                            }
                            Err(_) => {
                                eprintln!("[CustomConnector] TCP connect timed out to {}", addr);
                                return Err(format!("TCP connect to {} timed out", addr));
                            }
                        };

                        let _ = tcp.set_nodelay(true);

                        let sni = if ignore_ssl_error { "pixiv-multiplatform" } else { &hostname };

                        let ssl = match ssl_connector_clone.configure().and_then(|c| c.into_ssl(sni)) {
                            Ok(ssl) => ssl,
                            Err(e) => {
                                eprintln!("[CustomConnector] SSL configure failed: {}", e);
                                return Err(format!("SSL configure failed: {}", e));
                            }
                        };

                        // 创建 SSL Stream
                        let mut tls_stream = match SslStream::new(ssl, tcp) {
                            Ok(stream) => stream,
                            Err(e) => {
                                eprintln!("[CustomConnector] Create SSL stream failed: {}", e);
                                return Err(format!("Create SSL stream failed: {}", e));
                            }
                        };

                        // 执行 TLS 握手，添加超时（10秒）
                        eprintln!("[CustomConnector] Starting TLS handshake to {}", addr);
                        let handshake_result = timeout(Duration::from_secs(10), Pin::new(&mut tls_stream).connect()).await;

                        match handshake_result {
                            Ok(Ok(_)) => {
                                eprintln!("[CustomConnector] TLS handshake successful to {}", addr);
                                Ok((addr, SslConnection(tls_stream)))
                            }
                            Ok(Err(e)) => {
                                eprintln!("[CustomConnector] TLS handshake failed to {}: {}", addr, e);
                                Err(format!("SSL handshake failed: {}", e))
                            }
                            Err(_) => {
                                eprintln!("[CustomConnector] TLS handshake timed out to {}", addr);
                                Err("SSL handshake timed out".to_string())
                            }
                        }
                    })
                })
                .collect();

            eprintln!("[CustomConnector] Starting concurrent connections to all IPs");
            match select_ok(connection_futures).await {
                Ok(((addr, conn), _remaining)) => {
                    eprintln!("[CustomConnector] Successfully connected to fastest IP: {}", addr);
                    Ok(conn)
                }
                Err(error_msg) => {
                    eprintln!("[CustomConnector] All connections failed: {}", error_msg);
                    Err(format!("All IP connections failed: {}", error_msg).into())
                }
            }
        })
    }
}

#[cfg(test)]
mod tests {
    use crate::{DnsResolver, KeqwestConnector, TokioExecutor, system};
    use hyper::{Body, Client, Request};
    use std::collections::HashMap;
    use std::sync::Arc;
    use std::time::Duration;
    use tokio::time::timeout;

    #[tokio::test]
    async fn test_hyper_client_with_connection_pool() {
        println!("starting test");
        // 创建 fallback IP 映射表
        let mut fallback_ips = HashMap::new();
        fallback_ips.insert("www.pixiv.net".to_string(), vec!["210.140.139.155".to_string()]);

        // 创建自定义DNS解析器，包含fallback IP逻辑
        let dns_resolver: DnsResolver = Arc::new({
            let fallback_ips = fallback_ips.clone();
            let system_resolver = system();

            move |hostname: &str| {
                let hostname = hostname.to_string();
                let fallback_ips = fallback_ips.clone();
                let system_resolver = system_resolver.clone();
                Box::pin(async move {
                    // 先尝试系统DNS解析
                    match system_resolver(&hostname).await {
                        Ok(mut dns_ips) => {
                            // 如果成功，追加fallback IPs
                            if let Some(fallback) = fallback_ips.get(&hostname) {
                                eprintln!("[DNS Resolver] Appending fallback IPs: {:?}", fallback);
                                dns_ips.extend(fallback.clone());
                            }
                            Ok(dns_ips)
                        }
                        Err(e) => {
                            // 如果失败，使用fallback IPs
                            eprintln!("[DNS Resolver] DNS resolution failed: {:?}, using fallback", e);
                            fallback_ips.get(&hostname).cloned().ok_or_else(|| {
                                Box::new(std::io::Error::new(std::io::ErrorKind::Other, format!("DNS resolution failed and no fallback for {}", hostname))) as Box<dyn std::error::Error + Send + Sync>
                            })
                        }
                    }
                })
            }
        });

        // 创建自定义 Connector，使用自定义DNS解析器（包含fallback IP），忽略SSL错误
        let connector = KeqwestConnector::new(
            Some(dns_resolver), // 使用自定义DNS解析器（包含fallback IP）
            true,               // 忽略SSL错误
        )
        .expect("Failed to create CustomConnector");

        let client = Client::builder().executor(TokioExecutor).build::<_, Body>(connector);

        {
            // 构建第一个请求
            let req1 = Request::builder()
                .method("GET")
                .uri("https://www.pixiv.net")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .body(Body::empty())
                .expect("build request");

            println!("Sending request...");
            let resp1 = match timeout(Duration::from_secs(30), client.request(req1)).await {
                Ok(Ok(resp)) => resp,
                Ok(Err(e)) => panic!("Request failed: {:?}", e),
                Err(_) => panic!("Request timed out after 30 seconds"),
            };
            println!("Request sent, reading response...");
            let status1 = resp1.status();
            let _bytes1 = match timeout(Duration::from_secs(30), hyper::body::to_bytes(resp1.into_body())).await {
                Ok(Ok(bytes)) => bytes,
                Ok(Err(e)) => panic!("Read body failed: {:?}", e),
                Err(_) => panic!("Read body timed out after 30 seconds"),
            };
            let body_1 = String::from_utf8_lossy(&_bytes1).to_string();

            println!("First request status = {}", status1);
            println!("First request body = {}", &body_1[0..100]);
        }

        //shouldn't start TLS connect.
        {
            // 构建第一个请求
            let req1 = Request::builder()
                .method("GET")
                .uri("https://www.pixiv.net")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .body(Body::empty())
                .expect("build request");

            println!("Sending request...");
            let resp1 = match timeout(Duration::from_secs(30), client.request(req1)).await {
                Ok(Ok(resp)) => resp,
                Ok(Err(e)) => panic!("Request failed: {:?}", e),
                Err(_) => panic!("Request timed out after 30 seconds"),
            };
            println!("Request sent, reading response...");
            let status1 = resp1.status();
            let _bytes1 = match timeout(Duration::from_secs(30), hyper::body::to_bytes(resp1.into_body())).await {
                Ok(Ok(bytes)) => bytes,
                Ok(Err(e)) => panic!("Read body failed: {:?}", e),
                Err(_) => panic!("Read body timed out after 30 seconds"),
            };
            let body_1 = String::from_utf8_lossy(&_bytes1).to_string();

            println!("Second request status = {}", status1);
            println!("Second request body = {}", &body_1[0..100]);
        }

        {
            // 构建第一个请求
            let req1 = Request::builder()
                .method("GET")
                .uri("https://www.baidu.com")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .body(Body::empty())
                .expect("build request");

            println!("Sending request...");
            let resp1 = match timeout(Duration::from_secs(30), client.request(req1)).await {
                Ok(Ok(resp)) => resp,
                Ok(Err(e)) => panic!("Request failed: {:?}", e),
                Err(_) => panic!("Request timed out after 30 seconds"),
            };
            println!("Request sent, reading response...");
            let status1 = resp1.status();
            let _bytes1 = match timeout(Duration::from_secs(30), hyper::body::to_bytes(resp1.into_body())).await {
                Ok(Ok(bytes)) => bytes,
                Ok(Err(e)) => panic!("Read body failed: {:?}", e),
                Err(_) => panic!("Read body timed out after 30 seconds"),
            };
            let body_1 = String::from_utf8_lossy(&_bytes1).to_string();

            println!("First request status = {}", status1);
            println!("First request body = {}", &body_1[0..100]);
        }
    }
}
