package tvremote.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

data class FoundTv(val name: String, val host: String)

/**
 * Finds Android TVs on the Wi-Fi: they advertise `_androidtvremote2._tcp` over mDNS.
 * Results are delivered on the main thread.
 */
class TvDiscovery(context: Context, private val onFound: (FoundTv) -> Unit) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val main = Handler(Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null

    // NsdManager resolves only one service at a time on older Android versions.
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (listener != null) return
        multicastLock = wifi.createMulticastLock("tvremote").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { main.post { stop() } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                main.post { pending.add(service); resolveNext() }
            }
        }
        listener = l
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        pending.clear()
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving || listener == null) return
        val next = pending.poll() ?: return
        resolving = true
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                main.post { resolving = false; resolveNext() }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val addresses = if (android.os.Build.VERSION.SDK_INT >= 34) serviceInfo.hostAddresses else listOfNotNull(serviceInfo.host)
                val host = (addresses.firstOrNull { it is java.net.Inet4Address } ?: addresses.firstOrNull())?.hostAddress
                main.post {
                    resolving = false
                    if (host != null && listener != null) onFound(FoundTv(serviceInfo.serviceName, host))
                    resolveNext()
                }
            }
        })
    }

    companion object {
        private const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
