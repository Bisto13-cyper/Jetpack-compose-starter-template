package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.superapp.app.features.dashboard.ACTION_GRANT_LOCATION
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Network information Android allows without special privileges. The Wi-Fi network name (SSID)
 * and BSSID are only readable with the location permission (an Android privacy rule), so they are
 * shown only after the user grants it; MAC address, IMEI and similar identifiers are never read.
 */
object NetworkCollector {

    @Suppress("DEPRECATION")
    fun collect(context: Context, locationGranted: Boolean): List<InfoSection> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val props: LinkProperties? = network?.let { cm.getLinkProperties(it) }

        val type = when {
            caps == null -> "Not connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        val addresses = props?.linkAddresses?.map { it.address }.orEmpty()
        val ipv4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        val ipv6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') }
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val connection = InfoSection(
            title = "Connection",
            rows = listOf(
                InfoRow("Connection type", type),
                InfoRow("Network available", if (network != null) "Yes" else "No"),
                InfoRow("Internet capability", if (caps == null) null else if (hasInternet) "Yes" else "No"),
                InfoRow("Internet verified", if (caps == null) null else if (validated) "Yes" else "No"),
                InfoRow("Metered", if (caps == null) null else if (cm.isActiveNetworkMetered) "Metered" else "Unmetered"),
                InfoRow("Interface", props?.interfaceName),
                InfoRow("Local IPv4", ipv4.joinToString("\n").ifEmpty { null }),
                InfoRow("Local IPv6", ipv6.joinToString("\n").ifEmpty { null }),
                InfoRow("DNS servers", props?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString("\n")?.ifEmpty { null }),
                InfoRow(
                    "Estimated downlink",
                    caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.let { "${it / 1000} Mbps" },
                    "Estimate from the network type, not a measured speed"
                ),
                InfoRow(
                    "Estimated uplink",
                    caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }?.let { "${it / 1000} Mbps" },
                    "Estimate from the network type, not a measured speed"
                )
            )
        )

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = if (onWifi) try { wifiManager?.connectionInfo } catch (e: SecurityException) { null } else null
        val ssid = info?.ssid?.trim('"')?.takeIf { locationGranted && it != "<unknown ssid>" && it.isNotBlank() }
        val bssid = info?.bssid?.takeIf { locationGranted && it != "02:00:00:00:00:00" }

        val wifi = InfoSection(
            title = "Wi-Fi",
            rows = listOf(
                InfoRow("Wi-Fi enabled", try { wifiManager?.isWifiEnabled?.let { if (it) "Yes" else "No" } } catch (e: SecurityException) { null }),
                InfoRow("Network name (SSID)", ssid, if (ssid == null) "Android requires the location permission to read this" else null),
                InfoRow("BSSID", bssid, if (bssid == null) "Android requires the location permission to read this" else null),
                InfoRow("Link speed", info?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" }),
                InfoRow("Signal strength", info?.rssi?.takeIf { it > -127 }?.let { "$it dBm" }),
                InfoRow("Frequency", info?.frequency?.takeIf { it > 0 }?.let { "$it MHz" })
            ),
            actionId = if (!locationGranted && onWifi) ACTION_GRANT_LOCATION else null,
            actionLabel = "Allow location to show Wi-Fi name",
            footnote = "Location is only used by Android to unlock the Wi-Fi name; this app never reads your position."
        )

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val mobile = InfoSection(
            title = "Mobile network",
            rows = listOf(
                InfoRow("Operator", tm?.networkOperatorName?.takeIf { it.isNotBlank() }),
                InfoRow("Country (network)", tm?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase()),
                InfoRow("Roaming", if (tm == null || tm.networkOperatorName.isNullOrBlank()) null else if (tm.isNetworkRoaming) "Yes" else "No"),
                InfoRow("Radio technology / signal", null, "Needs the phone-state permission, which this app does not request")
            )
        )
        return listOf(connection, wifi, mobile)
    }
}
