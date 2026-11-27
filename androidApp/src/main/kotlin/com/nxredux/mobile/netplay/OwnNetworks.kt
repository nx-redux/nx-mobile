package com.nxredux.mobile.netplay

import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * IPv4 networks this phone serves itself: its hotspot most often (a Wi-Fi Direct group or USB tethering too). Android
 * lists no `Network` for them, so a socket pinned to the Wi-Fi never reaches them; they are the interfaces up with a
 * broadcast address that no `Network` is on.
 */
internal object OwnNetworks {
    data class Subnet(val broadcast: Int, val mask: Int)

    @Suppress("DEPRECATION")
    fun list(cm: ConnectivityManager?): List<Subnet> {
        cm ?: return emptyList()
        val onNetworks = cm.allNetworks.mapNotNull { runCatching { cm.getLinkProperties(it)?.interfaceName }.getOrNull() }.toSet()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull() ?: return emptyList()
        return interfaces.asSequence()
            .filter { it.name !in onNetworks && runCatching { it.isUp && !it.isLoopback && !it.isPointToPoint }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { a ->
                val ip = a.address as? Inet4Address ?: return@mapNotNull null
                if (a.broadcast == null) return@mapNotNull null
                subnet(ip.address, a.networkPrefixLength.toInt())
            }
            .distinct().take(MAX).toList()
    }

    /** [address]'s network as broadcast address and mask, host order; null for a prefix no hotspot hands out. */
    fun subnet(address: ByteArray, prefix: Int): Subnet? {
        if (address.size != 4 || prefix !in 8..30) return null
        val ip = address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val mask = -1 shl (32 - prefix)
        return Subnet(ip or mask.inv(), mask)
    }

    /** The native link keeps this many (NDL_LOCAL_MAX). */
    const val MAX = 4
}
