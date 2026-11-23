package de.gumerbaev.actual.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LocalNetworkHelperTest {

    @Test
    fun testLocalNetworkAddressesIpv4() {
        val localIps = listOf(
            // RFC 1918
            "10.0.0.1",
            "10.255.255.254",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.0.1",
            "192.168.178.42",
            // Link-local
            "169.254.0.1",
            "169.254.254.254",
            // CGNAT 100.64.0.0/10
            "100.64.0.1",
            "100.127.255.254",
            // Multicast & Broadcast
            "224.0.0.1",
            "239.255.255.250",
            "255.255.255.255"
        )
        for (ip in localIps) {
            assertTrue("Expected $ip to be local network", LocalNetworkHelper.isLocalNetworkAddress(InetAddress.getByName(ip)))
        }

        val nonLocalIps = listOf(
            "8.8.8.8",
            "1.1.1.1",
            "172.15.255.255",
            "172.32.0.1",
            "100.63.255.255",
            "100.128.0.1",
            "192.167.1.1",
            "127.0.0.1"
        )
        for (ip in nonLocalIps) {
            assertFalse("Expected $ip not to be local network", LocalNetworkHelper.isLocalNetworkAddress(InetAddress.getByName(ip)))
        }
    }

    @Test
    fun testLocalNetworkAddressesIpv6() {
        val localIps = listOf(
            "fc00::1",
            "fd12:3456:789a::1",
            "fe80::1",
            "ff02::fb"
        )
        for (ip in localIps) {
            assertTrue("Expected $ip to be local network", LocalNetworkHelper.isLocalNetworkAddress(InetAddress.getByName(ip)))
        }

        val nonLocalIps = listOf(
            "::1",
            "2001:4860:4860::8888",
            "2606:4700:4700::1111",
            "fbff::1",
            "fe00::1"
        )
        for (ip in nonLocalIps) {
            assertFalse("Expected $ip not to be local network", LocalNetworkHelper.isLocalNetworkAddress(InetAddress.getByName(ip)))
        }
    }

    @Test
    fun testIsLocalHostName() {
        assertTrue(LocalNetworkHelper.isLocalHostName("actual.local"))
        assertTrue(LocalNetworkHelper.isLocalHostName("MY-SERVER.LOCAL."))
        assertTrue(LocalNetworkHelper.isLocalHostName("nas.home.local"))

        assertFalse(LocalNetworkHelper.isLocalHostName(".local"))
        assertFalse(LocalNetworkHelper.isLocalHostName("local"))
        assertFalse(LocalNetworkHelper.isLocalHostName("localhost"))
        assertFalse(LocalNetworkHelper.isLocalHostName("actual.example.com"))
        assertFalse(LocalNetworkHelper.isLocalHostName("local.example.com"))
        assertFalse(LocalNetworkHelper.isLocalHostName(""))
    }

    @Test
    fun testResolvesToLocalNetwork() = runBlocking {
        // Split-horizon DNS domain resolving to a private IP on LAN
        assertTrue(
            LocalNetworkHelper.resolvesToLocalNetwork("https://actual.example.com/transaction") {
                arrayOf(InetAddress.getByAddress(it, byteArrayOf(192.toByte(), 168.toByte(), 1, 100)))
            }
        )

        // Public IP resolution
        assertFalse(
            LocalNetworkHelper.resolvesToLocalNetwork("https://actual.example.com/transaction") {
                arrayOf(InetAddress.getByAddress(it, byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
            }
        )

        // Mixed public and local DNS records
        assertTrue(
            LocalNetworkHelper.resolvesToLocalNetwork("https://actual.example.com/transaction") {
                arrayOf(
                    InetAddress.getByAddress(it, byteArrayOf(93, 184.toByte(), 216.toByte(), 34)),
                    InetAddress.getByAddress(it, byteArrayOf(10, 0, 0, 5))
                )
            }
        )

        // .local mDNS hostname even if resolver fails
        assertTrue(
            LocalNetworkHelper.resolvesToLocalNetwork("http://actual.local:5006/transaction") {
                throw UnknownHostException("mDNS blocked")
            }
        )

        // Non-.local hostname when DNS fails
        assertFalse(
            LocalNetworkHelper.resolvesToLocalNetwork("https://actual.example.com/transaction") {
                throw UnknownHostException("DNS failed")
            }
        )

        // Literal IP URLs
        assertTrue(
            LocalNetworkHelper.resolvesToLocalNetwork("http://192.168.178.42:3000/transaction") {
                arrayOf(InetAddress.getByName(it))
            }
        )
        assertTrue(
            LocalNetworkHelper.resolvesToLocalNetwork("http://[fd00::10]:3000/transaction") {
                arrayOf(InetAddress.getByName(it))
            }
        )
        assertFalse(
            LocalNetworkHelper.resolvesToLocalNetwork("http://127.0.0.1:3000/transaction") {
                arrayOf(InetAddress.getByName(it))
            }
        )

        // Invalid or blank URLs
        assertFalse(LocalNetworkHelper.resolvesToLocalNetwork("") { error("Should not resolve blank URL") })
        assertFalse(LocalNetworkHelper.resolvesToLocalNetwork("not a url") { error("Should not resolve invalid URL") })
    }
}
