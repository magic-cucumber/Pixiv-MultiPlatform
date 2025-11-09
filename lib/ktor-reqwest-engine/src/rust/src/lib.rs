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
    use tokio::io::{AsyncRead, AsyncWrite};
    use tokio::net::TcpStream;
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

    // 自定义 Connector Service
    #[derive(Clone)]
    struct CustomConnector {
        ip: String,
        sni_hostname: String,
        ssl_connector: SslConnector,
    }

    impl Service<Uri> for CustomConnector {
        type Response = SslConnection;
        type Error = Box<dyn std::error::Error + Send + Sync>;
        type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

        fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }

        fn call(&mut self, uri: Uri) -> Self::Future {
            let ip = self.ip.clone();
            let sni_hostname = self.sni_hostname.clone();
            let ssl_connector = self.ssl_connector.clone();

            Box::pin(async move {
                // 从 URI 中提取端口
                let port = uri.port_u16().unwrap_or(443);
                let addr = format!("{}:{}", ip, port);

                // 建立 TCP 连接
                let tcp = TcpStream::connect(&addr).await.map_err(|e| format!("TCP connect failed: {}", e))?;
                let _ = tcp.set_nodelay(true);

                // 配置 SSL，使用 SNI 主机名而不是 URI 中的主机名
                let ssl = ssl_connector.configure().and_then(|c| c.into_ssl(&sni_hostname)).map_err(|e| format!("SSL configure failed: {}", e))?;

                // 创建 SSL Stream
                let mut tls_stream = SslStream::new(ssl, tcp).map_err(|e| format!("Create SSL stream failed: {}", e))?;

                // 执行 TLS 握手
                Pin::new(&mut tls_stream).connect().await.map_err(|e| format!("SSL handshake failed: {}", e))?;

                Ok(SslConnection(tls_stream))
            })
        }
    }

    // 使用 hyper 上层 API 和自定义 Connector 的测试
    #[tokio::test]
    async fn test_hyper_client_with_custom_connector() {
        let ip = "210.140.139.155";
        let sni_hostname = "www.pixivision.net";

        // 创建 SSL Connector
        let mut builder = SslConnector::builder(SslMethod::tls()).expect("create ssl builder failed");
        builder.set_verify(SslVerifyMode::NONE);
        let ssl_connector = builder.build();

        // 创建自定义 Connector
        let connector = CustomConnector { ip: ip.to_string(), sni_hostname: sni_hostname.to_string(), ssl_connector };

        // 使用 hyper 的上层 API 创建客户端，传入自定义 Connector 和 executor
        let client = Client::builder().executor(TokioExecutor).build::<_, Body>(connector);

        // 构建请求
        let req = Request::builder()
            .method("GET")
            .uri("https://www.pixiv.net")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
            .body(Body::empty())
            .expect("build request");

        // 使用上层 API 发送请求
        let resp = client.request(req).await.expect("request failed");

        let status = resp.status();
        let bytes = hyper::body::to_bytes(resp.into_body()).await.expect("read body failed");
        let body_text = String::from_utf8_lossy(&bytes).to_string();

        println!("status = {}", status);
        println!("body (first 300 chars) = {}", &body_text.chars().take(300).collect::<String>());

        // 断言：成功或重定向都认为是 SNI 绕过成功
        assert!(status.is_success() || status.is_redirection(), "unexpected status: {}", status);
    }
}
