mod jvm;

#[cfg(test)]
mod tests {
    use hyper::client::connect::{Connected, Connection};
    use hyper::service::Service;
    use hyper::{Body, Client, Request, Uri};
    use openssl::ssl::{SslConnector, SslMethod, SslVerifyMode};
    use std::future::Future;
    use std::io;
    use std::pin::Pin;
    use std::task::{Context, Poll};
    use std::sync::Arc;
    use std::collections::HashMap;
    use tokio::io::{AsyncRead, AsyncWrite};
    use tokio::net::{lookup_host, TcpStream};
    use tokio::time::{timeout, Duration};
    use tokio_openssl::SslStream;

    // single tcp connection
    #[tokio::test]
    async fn test_pixiv_request_fallback() {
        let ip = "210.140.139.155";
        let addr = format!("{}:443", ip);
        let sni_hostname = "www.pixivision.net";
        let tcp = match TcpStream::connect(&addr).await {
            Ok(s) => s,
            Err(e) => panic!("tcp connect to {} failed: {}", addr, e),
        };
        let _ = tcp.set_nodelay(true);

        let mut builder = SslConnector::builder(SslMethod::tls()).expect("create ssl builder failed");
        builder.set_verify(SslVerifyMode::NONE);
        let connector = builder.build();

        let ssl = match connector.configure().and_then(|c| c.into_ssl(sni_hostname)) {
            Ok(s) => s,
            Err(e) => panic!("prepare ssl failed: {}", e),
        };

        let mut tls_stream: SslStream<TcpStream> = match SslStream::new(ssl, tcp) {
            Ok(s) => s,
            Err(e) => panic!("create ssl stream failed: {}", e),
        };
        if let Err(e) = Pin::new(&mut tls_stream).connect().await {
            panic!("ssl connect (handshake) failed: {}", e);
        }

        let (mut sender, connection) = match hyper::client::conn::handshake(tls_stream).await {
            Ok(pair) => pair,
            Err(e) => panic!("hyper handshake failed: {}", e),
        };

        tokio::spawn(async move {
            if let Err(e) = connection.await {
                eprintln!("hyper connection error: {}", e);
            }
        });

        let req = Request::builder()
            .method("GET")
            .uri("https://www.pixiv.net/")
            .header("Host", sni_hostname)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
            .body(Body::empty())
            .expect("build request");

        let resp = match sender.send_request(req).await {
            Ok(r) => r,
            Err(e) => panic!("send_request failed: {}", e),
        };

        let status = resp.status();
        let bytes = match hyper::body::to_bytes(resp.into_body()).await {
            Ok(b) => b,
            Err(e) => panic!("read body failed: {}", e),
        };
        let body_text = String::from_utf8_lossy(&bytes).to_string();

        println!("status = {}", status);
        println!("body (first 300 chars) = {}", &body_text.chars().take(300).collect::<String>());

        // 断言：成功或重定向都认为是 SNI 绕过成功（根据需要可调整断言）
        assert!(status.is_success() || status.is_redirection(), "unexpected status: {}", status);
    }

    // 包装 SslStream 以实现 Connection trait
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
        F: std::future::Future<Output = ()> + Send + 'static,
    {
        fn execute(&self, fut: F) {
            tokio::spawn(fut);
        }
    }

    // DNS解析器类型别名
    type DnsResolver = Arc<dyn Fn(&str) -> Pin<Box<dyn Future<Output = Result<Vec<String>, Box<dyn std::error::Error + Send + Sync>>> + Send>> + Send + Sync>;

    #[derive(Clone, Debug)]
    struct FallbackTarget {
        ip: String,
        sni_hostname: Option<String>,
    }

    impl FallbackTarget {
        fn new(ip: impl Into<String>, sni_hostname: Option<impl Into<String>>) -> Self {
            Self {
                ip: ip.into(),
                sni_hostname: sni_hostname.map(|s| s.into()),
            }
        }
    }

    type FallbackMap = HashMap<String, Vec<FallbackTarget>>;

    // 自定义 Connector Service
    #[derive(Clone)]
    struct CustomConnector {
        dns_resolver: DnsResolver,
        fallback_ips: FallbackMap,
        ssl_connector: SslConnector,
    }

    impl CustomConnector {
        /// 创建新的 CustomConnector
        ///
        /// # 参数
        /// - `dns_resolver`: DNS解析闭包，输入域名，返回IP列表或error，默认值仅为调用系统DNS
        /// - `ignore_ssl_error`: 是否忽略SSL错误，该属性影响 `builder.set_verify(SslVerifyMode::NONE)`
        /// - `fallback_ips`: 特定域名的fallback IP映射表，当DNS解析失败时使用
        fn new(
            dns_resolver: Option<DnsResolver>,
            ignore_ssl_error: bool,
            fallback_ips: FallbackMap,
        ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
            // 默认DNS解析器：调用系统DNS
            let default_dns_resolver: DnsResolver = Arc::new(|hostname: &str| {
                let hostname = hostname.to_string();
                Box::pin(async move {
                    eprintln!("[DNS Resolver] Resolving hostname: {}", hostname);
                    // 调用系统DNS解析（使用任意端口，因为我们只需要IP地址）
                    let addr = format!("{}:0", hostname);
                    match lookup_host(&addr).await {
                        Ok(addrs) => {
                            let ips: Vec<String> = addrs
                                .map(|addr| addr.ip().to_string())
                                .collect();
                            eprintln!("[DNS Resolver] Resolved IPs: {:?}", ips);
                            if ips.is_empty() {
                                Err("No IP addresses found".into())
                            } else {
                                Ok(ips)
                            }
                        }
                        Err(e) => {
                            eprintln!("[DNS Resolver] Resolution failed: {:?}", e);
                            Err(Box::new(e) as Box<dyn std::error::Error + Send + Sync>)
                        }
                    }
                })
            });

            let dns_resolver = dns_resolver.unwrap_or(default_dns_resolver);

            // 创建 SSL Connector
            let mut builder = SslConnector::builder(SslMethod::tls())
                .map_err(|e| format!("create ssl builder failed: {}", e))?;
            if ignore_ssl_error {
                builder.set_verify(SslVerifyMode::NONE);
            }
            let ssl_connector = builder.build();

            Ok(Self {
                dns_resolver,
                fallback_ips,
                ssl_connector,
            })
        }
    }

    impl Service<Uri> for CustomConnector {
        type Response = SslConnection;
        type Error = Box<dyn std::error::Error + Send + Sync>;
        type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

        fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }

        fn call(&mut self, uri: Uri) -> Self::Future {
            let dns_resolver = self.dns_resolver.clone();
            let fallback_ips = self.fallback_ips.clone();
            let ssl_connector = self.ssl_connector.clone();

            Box::pin(async move {
                // 从 URI 中提取主机名和端口
                let hostname = uri.host().ok_or("No hostname in URI")?.to_string();
                let port = uri.port_u16().unwrap_or(443);

                eprintln!("[CustomConnector] Resolving DNS for hostname: {}", hostname);
                // 调用DNS解析器获取IP列表，添加1秒超时
                let mut candidates = match timeout(Duration::from_secs(1), dns_resolver(&hostname)).await {
                    Ok(Ok(dns_ips)) => {
                        eprintln!("[CustomConnector] DNS resolved successfully: {:?}", dns_ips);
                        let mut entries: Vec<FallbackTarget> = dns_ips
                            .into_iter()
                            .map(|ip| FallbackTarget::new(ip, Option::<String>::None))
                            .collect();
                        if let Some(fallback) = fallback_ips.get(&hostname) {
                            eprintln!("[CustomConnector] Appending fallback IPs: {:?}", fallback);
                            entries.extend(fallback.clone());
                        }
                        entries
                    }
                    Ok(Err(e)) => {
                        eprintln!("[CustomConnector] DNS resolution failed: {:?}, using fallback", e);
                        // DNS解析失败，使用该域名的fallback IP列表
                        fallback_ips.get(&hostname).cloned().unwrap_or_default()
                    }
                    Err(_) => {
                        eprintln!("[CustomConnector] DNS resolution timed out, using fallback");
                        // DNS解析超时，使用该域名的fallback IP列表
                        fallback_ips.get(&hostname).cloned().unwrap_or_default()
                    }
                };

                // 如果DNS解析返回空列表，也使用fallback
                if candidates.is_empty() {
                    eprintln!("[CustomConnector] DNS returned empty list, using fallback");
                    candidates = fallback_ips.get(&hostname).cloned().unwrap_or_default();
                }

                let debug_ips: Vec<&str> = candidates.iter().map(|entry| entry.ip.as_str()).collect();
                eprintln!("[CustomConnector] Final IP list: {:?}", debug_ips);

                // 如果仍然为空，返回错误
                if candidates.is_empty() {
                    return Err("No IP addresses available (DNS failed and no fallback)".into());
                }

                // 尝试连接第一个可用的IP
                let mut last_error = None;
                for candidate in candidates {
                    let addr = format!("{}:{}", candidate.ip, port);
                    eprintln!("[CustomConnector] Attempting to connect to: {}", addr);

                    // TCP 连接超时（5秒）
                    match timeout(Duration::from_secs(5), TcpStream::connect(&addr)).await {
                        Ok(Ok(tcp)) => {
                            eprintln!("[CustomConnector] TCP connection established");
                            let _ = tcp.set_nodelay(true);

                            // 配置 SSL，使用从 URI 中获取的主机名作为 SNI hostname
                            let sni_to_use = candidate
                                .sni_hostname
                                .as_deref()
                                .unwrap_or(&hostname);
                            eprintln!("[CustomConnector] Configuring SSL with SNI hostname: {}", sni_to_use);
                            let ssl = ssl_connector
                                .configure()
                                .and_then(|mut c| {
                                    c.set_verify_hostname(false);
                                    c.set_hostname(&hostname).expect("TODO: panic message");
                                    c.into_ssl("aaaa")
                                })
                                .map_err(|e| format!("SSL configure failed: {}", e))?;

                            // 创建 SSL Stream
                            let mut tls_stream = SslStream::new(ssl, tcp)
                                .map_err(|e| format!("Create SSL stream failed: {}", e))?;

                            // 执行 TLS 握手，添加超时（10秒）
                            eprintln!("[CustomConnector] Starting TLS handshake");
                            match timeout(Duration::from_secs(10), Pin::new(&mut tls_stream).connect()).await {
                                Ok(Ok(_)) => {
                                    eprintln!("[CustomConnector] TLS handshake successful");
                                    return Ok(SslConnection(tls_stream));
                                }
                                Ok(Err(e)) => {
                                    eprintln!("[CustomConnector] TLS handshake failed: {}", e);
                                    last_error = Some(format!("SSL handshake failed: {}", e));
                                    continue;
                                }
                                Err(_) => {
                                    eprintln!("[CustomConnector] TLS handshake timed out");
                                    last_error = Some("SSL handshake timed out".to_string());
                                    continue;
                                }
                            }
                        }
                        Ok(Err(e)) => {
                            eprintln!("[CustomConnector] TCP connect failed: {}", e);
                            last_error = Some(format!("TCP connect to {} failed: {}", addr, e));
                            continue;
                        }
                        Err(_) => {
                            eprintln!("[CustomConnector] TCP connect timed out");
                            last_error = Some(format!("TCP connect to {} timed out", addr));
                            continue;
                        }
                    }
                }

                // 所有IP都连接失败
                Err(format!("All IP addresses failed. Last error: {}", last_error.unwrap_or_else(|| "Unknown error".to_string())).into())
            })
        }
    }


    #[tokio::test]
    async fn test_hyper_client_with_connection_pool() {
        println!("starting test");
        // 创建 fallback IP 映射表
        let mut fallback_ips = HashMap::new();
        fallback_ips.insert(
            "www.pixiv.net".to_string(),
            vec![FallbackTarget::new("210.140.139.155", Some("www.pixiv.net"))],
        );

        // 创建自定义 Connector，使用默认DNS解析器，忽略SSL错误，使用fallback IP
        let connector = CustomConnector::new(
            None, // 使用默认DNS解析器（系统DNS）
            true, // 忽略SSL错误
            fallback_ips, // fallback IP映射表
        ).expect("Failed to create CustomConnector");

        // hyper::Client 默认支持连接复用（连接池）
        // 它会自动管理连接的创建、复用和清理
        let client = Client::builder()
            .executor(TokioExecutor)
            .build::<_, Body>(connector);

        // 构建第一个请求
        let req1 = Request::builder()
            .method("GET")
            .uri("https://www.pixiv.net")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
            .body(Body::empty())
            .expect("build request");

        // 发送第一个请求（会建立新连接），添加30秒超时
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
        println!("First request body = {}", body_1);
        assert!(status1.is_success() || status1.is_redirection(), "unexpected status: {}", status1);
    }
}
