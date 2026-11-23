package de.gumerbaev.actual.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat
import de.gumerbaev.actual.R
import de.gumerbaev.actual.network.ActualApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

object LocalNetworkHelper {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CINNAMON_BUN)
    fun isPermissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    fun hasLocalNetworkPermission(context: Context): Boolean {
        if (!isPermissionRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocalHostName(host: String): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        return normalized.endsWith(".local") && normalized.length > ".local".length
    }

    fun isLocalNetworkAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress) {
            return true
        }
        if (address is Inet4Address) {
            val bytes = address.address
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            // CGNAT 100.64.0.0/10
            if (b0 == 100 && (b1 and 0xC0) == 64) return true
            // IPv4 broadcast 255.255.255.255
            if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) return true
        }
        if (address is Inet6Address) {
            val bytes = address.address
            val b0 = bytes[0].toInt() and 0xFF
            // IPv6 Unique Local Address fc00::/7
            if ((b0 and 0xFE) == 0xFC) return true
        }
        return false
    }

    suspend fun resolvesToLocalNetwork(
        serverUrl: String,
        resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }
    ): Boolean = withContext(Dispatchers.IO) {
        val host = serverUrl.trim().toHttpUrlOrNull()?.host ?: return@withContext false
        if (isLocalHostName(host)) return@withContext true

        val addresses = try {
            resolver(host)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext false
        }
        addresses.any { isLocalNetworkAddress(it) }
    }

    suspend fun enrichErrorIfLocalNetworkBlocked(
        context: Context,
        serverUrl: String,
        result: ActualApiClient.ApiResult
    ): ActualApiClient.ApiResult {
        if (result.success || result.httpCode > 0 || hasLocalNetworkPermission(context)) {
            return result
        }
        return if (resolvesToLocalNetwork(serverUrl)) {
            result.copy(error = UiText.res(R.string.err_local_network_permission))
        } else {
            result
        }
    }

    fun isLocalNetworkPermissionError(result: ActualApiClient.ApiResult): Boolean {
        return result.error is UiText.Res && result.error.resId == R.string.err_local_network_permission
    }
}
