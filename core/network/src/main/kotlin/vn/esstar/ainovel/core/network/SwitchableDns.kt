package vn.esstar.ainovel.core.network

import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import timber.log.Timber
import vn.esstar.ainovel.core.datastore.DohMode

/**
 * A [Dns] whose backend can be switched at runtime (system resolver, Google DoH, Cloudflare DoH).
 * DoH resolvers fall back to the system resolver when the DoH endpoint itself cannot be reached.
 */
@Singleton
class SwitchableDns @Inject constructor() : Dns {

    @Volatile
    var mode: DohMode = DohMode.SYSTEM

    private val bootstrap: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    private val google: Dns by lazy {
        DnsOverHttps.Builder().client(bootstrap)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
            .build()
    }

    private val cloudflare: Dns by lazy {
        DnsOverHttps.Builder().client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val resolver = when (mode) {
            DohMode.SYSTEM -> return Dns.SYSTEM.lookup(hostname)
            DohMode.GOOGLE -> google
            DohMode.CLOUDFLARE -> cloudflare
        }
        return try {
            resolver.lookup(hostname)
        } catch (e: UnknownHostException) {
            Timber.w("DoH lookup failed for %s, falling back to system DNS", hostname)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
