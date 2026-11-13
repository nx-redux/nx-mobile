package com.nxredux.mobile.netplay

import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/** The Wi-Fi `Network`, this phone's IPv4 on it and its broadcast address; shared by netplay and Device Sync. */
internal object WifiNetworks {
    /**
     * The Wi-Fi network: the active one when it is Wi-Fi, else any connected Wi-Fi network (a VPN may be active). Never
     * a VPN running over the Wi-Fi, which carries the Wi-Fi transport too (the USB cable's is one).
     */
    @Suppress("DEPRECATION")
    fun network(cm: ConnectivityManager?): Network? {
        cm ?: return null
        fun isWifi(n: Network?) = n != null && cm.getNetworkCapabilities(n)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } == true
        cm.activeNetwork?.let { if (isWifi(it)) return it }
        return cm.allNetworks.firstOrNull { isWifi(it) }
    }

    /** This phone's IPv4 link address on [network] (the Wi-Fi one by default). */
    fun address(cm: ConnectivityManager?, network: Network? = network(cm)): LinkAddress? =
        network?.let { cm?.getLinkProperties(it) }?.linkAddresses?.firstOrNull { it.address is Inet4Address }

    /** The IPv4 directed broadcast (address | ~mask) of [link]; null when unknown. */
    fun broadcast(link: LinkAddress?): InetAddress? {
        val addr = link?.address as? Inet4Address ?: return null
        val prefix = link.prefixLength.coerceIn(0, 32)
        val ip = addr.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val bc = ip or mask.inv()
        return InetAddress.getByAddress(byteArrayOf((bc ushr 24).toByte(), (bc ushr 16).toByte(), (bc ushr 8).toByte(), bc.toByte()))
    }
}
