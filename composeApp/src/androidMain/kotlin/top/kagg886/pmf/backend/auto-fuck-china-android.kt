package top.kagg886.pmf.backend

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import java.io.ByteArrayOutputStream
import java.util.Base64
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import top.kagg886.pmf.util.logger

private fun buildDnsQuery(name: String): ByteArray {
    val out = ByteArrayOutputStream()
    // Header: ID=0, Flags=RD(0x0100), QDCOUNT=1, AN/NS/AR=0
    out.write(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
    // QNAME
    for (label in name.split(".")) {
        out.write(label.length)
        out.write(label.toByteArray(Charsets.US_ASCII))
    }
    out.write(0x00)
    // QTYPE=A(1), QCLASS=IN(1)
    out.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
    return out.toByteArray()
}

// compression pointer
private fun skipName(bytes: ByteArray, pos: Int): Int {
    var p = pos
    while (p < bytes.size) {
        val len = bytes[p].toInt() and 0xFF
        when {
            len == 0 -> return p + 1
            len and 0xC0 == 0xC0 -> return p + 2
            else -> p += len + 1
        }
    }
    return p
}

private fun parseDnsARecords(bytes: ByteArray): List<String> {
    val ancount = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
    if (ancount == 0) return emptyList()

    // Skip header (12 bytes) and the single question entry
    var pos = skipName(bytes, 12) + 4  // +4 for QTYPE + QCLASS

    val addresses = mutableListOf<String>()
    repeat(ancount) {
        pos = skipName(bytes, pos)
        val type = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
        val rdlength = ((bytes[pos + 8].toInt() and 0xFF) shl 8) or (bytes[pos + 9].toInt() and 0xFF)
        pos += 10  // TYPE(2) + CLASS(2) + TTL(4) + RDLENGTH(2)
        if (type == 1 && rdlength == 4) {   // A record
            addresses.add("${bytes[pos].toInt() and 0xFF}.${bytes[pos + 1].toInt() and 0xFF}.${bytes[pos + 2].toInt() and 0xFF}.${bytes[pos + 3].toInt() and 0xFF}")
        }
        pos += rdlength
    }
    return addresses
}

fun OkHttpClient.Builder.bypassSNIOnAndroid(
    queryUrl: String,
    dohTimeout: Int = 5,
    unsafeSSL: Boolean = true,
    fallback: Map<String, List<String>>,
) = dns(SNIReplaceDNS(queryUrl, dohTimeout, unsafeSSL, fallback)).sslSocketFactory(BypassSSLSocketFactory, BypassTrustManager)

private data class SNIReplaceDNS(
    val queryUrl: String,
    val dohTimeout: Int = 5,
    val unsafeSSL: Boolean = true,
    val fallback: Map<String, List<String>>,
) : Dns {
    private val client = OkHttpClient.Builder().apply {
        if (unsafeSSL) {
            ignoreSSL()
        }
        callTimeout(dohTimeout.seconds.toJavaDuration())
    }.build()
    override fun lookup(hostname: String): List<InetAddress> {
        val data = try {
            val host = when {
                hostname.endsWith("pixiv.net") -> {
                    "www.pixivision.net"
                }

                else -> hostname
            }
            val dnsQuery = Base64.getUrlEncoder().withoutPadding().encodeToString(buildDnsQuery(host))
            val resp = client.newCall(
                Request.Builder()
                    .url("$queryUrl?dns=$dnsQuery")
                    .header("Accept", "application/dns-message")
                    .build(),
            ).execute()
            parseDnsARecords(resp.body!!.bytes()).map {
                InetAddress.getByName(it)
            }
        } catch (e: Throwable) {
            logger.w(e) { "query DoH failed, use system dns" }

            // FIXME 不知道什么情况下fallback前置好用
            fallback[hostname]!!.map { InetAddress.getAllByName(it)!!.toList() }.flatten() + Dns.SYSTEM.lookup(hostname)
        }
        logger.d("DNS lookup $hostname result : $data")
        return data
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
    @Throws(IOException::class)
    override fun createSocket(paramSocket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val inetAddress = paramSocket!!.inetAddress
        val sslSocket =
            (getDefault().createSocket(inetAddress, port) as SSLSocket).apply { enabledProtocols = supportedProtocols }
        return sslSocket
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
