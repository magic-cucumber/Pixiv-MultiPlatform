package top.kagg886.pmf.backend

import arrow.core.getOrElse
import arrow.fx.coroutines.raceN
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import top.kagg886.pmf.util.logger

fun OkHttpClient.Builder.bypassSNIOnAndroid(
    queryUrl: String,
    dohTimeout: Int = 5,
    unsafeSSL: Boolean = true,
    fallback: Map<String, List<String>>,
) = dns(SNIReplaceDNS(queryUrl, dohTimeout, unsafeSSL, fallback))
    .sslSocketFactory(
        BypassSSLSocketFactory,
        BypassTrustManager,
    )

private data class SNIReplaceDNS(
    val queryUrl: String,
    val dohTimeout: Int = 5,
    val unsafeSSL: Boolean = true,
    val fallback: Map<String, List<String>>,
) : Dns {
    private val dnsOverHttps = DnsOverHttps.Builder()
        .client(
            client = OkHttpClient.Builder().apply {
                if (unsafeSSL) {
                    ignoreSSL()
                }
                callTimeout(dohTimeout.seconds.toJavaDuration())
            }.build(),
        )
        .systemDns(Dns.SYSTEM)
        .url(queryUrl.toHttpUrl())
        .build()

    override fun lookup(hostname: String): List<InetAddress> = runBlocking(Dispatchers.IO) {
        val host = when {
            hostname.endsWith("pixiv.net") -> "pixiv.net"
            else -> hostname
        }

        val result = raceN(
            {
                try {
                    val result = dnsOverHttps.lookup(host)
                    yield()
                    if (result.isEmpty()) {
                        logger.w("query DoH returned none, use system dns")
                    }
                    result
                } catch (e: Exception) {
                    yield()
                    logger.w(e) { "query DoH failed, use system dns" }
                    emptyList()
                }
            },
            {
                try {
                    val result = Dns.SYSTEM.lookup("$host.cdn.cloudflare.net")
                    yield()
                    if (result.isEmpty()) {
                        logger.w("query DoH returned none, use system dns")
                    }
                    result
                } catch (e: Exception) {
                    yield()
                    logger.w(e) { "query dns failed, use fallback dns" }
                    emptyList()
                }
            },
        ).getOrElse { emptyList() }


        val fallback = fallback[hostname]!!.flatMap { InetAddress.getAllByName(it)!!.toList() }

        (result + fallback).distinct()
    }
}

fun OkHttpClient.Builder.ignoreSSL() {
    val sslContext = SSLContext.getInstance("SSL")
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(
            p0: Array<out X509Certificate>?,
            p1: String?,
        ) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    sslContext.init(
        null,
        arrayOf(
            trust,
        ),
        SecureRandom(),
    )

    sslSocketFactory(sslContext.socketFactory, trust)
    hostnameVerifier { _, _ -> true }
}

private object BypassSSLSocketFactory : SSLSocketFactory() {
    private val factory: SSLSocketFactory by lazy {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        context.socketFactory
    }

    @Throws(IOException::class)
    override fun createSocket(paramSocket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val socket = factory.createSocket(paramSocket, host, port, autoClose) as SSLSocket
        val params = socket.getSSLParameters()
        params.serverNames = listOf(SNIHostName("pixiv.me"))
        socket.sslParameters = params
        return socket
    }

    override fun createSocket(paramString: String?, paramInt: Int): Socket? = null

    override fun createSocket(
        paramString: String?,
        paramInt1: Int,
        paramInetAddress: InetAddress?,
        paramInt2: Int,
    ): Socket? = null

    override fun createSocket(paramInetAddress: InetAddress?, paramInt: Int): Socket? = null

    override fun createSocket(
        paramInetAddress1: InetAddress?,
        paramInt1: Int,
        paramInetAddress2: InetAddress?,
        paramInt2: Int,
    ): Socket? = null

    override fun getDefaultCipherSuites(): Array<String> = arrayOf()

    override fun getSupportedCipherSuites(): Array<String> = arrayOf()
}

@Suppress("CustomX509TrustManager")
private object BypassTrustManager : X509TrustManager {
    @Suppress("TrustAllX509TrustManager")
    override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, s: String) = Unit

    @Suppress("TrustAllX509TrustManager")
    override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, s: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}
