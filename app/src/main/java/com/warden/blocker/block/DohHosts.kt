package com.warden.blocker.block

/**
 * Well-known DNS-over-HTTPS / DoT resolver hostnames. When DoH blocking is on, these are
 * added to the sinkhole so a browser's encrypted-DNS bootstrap fails and it falls back to
 * the system resolver — which Warden filters. Not foolproof (clients with hardcoded IPs
 * still slip through), but it closes the common case. Subdomain matches are handled by the
 * VPN's isBlocked() check.
 */
object DohHosts {
    val ALL: List<String> = listOf(
        "dns.google",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "doh.opendns.com",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "doh.cleanbrowsing.org",
        "dns.nextdns.io",
        "doh.dns.sb",
        "dns.controld.com",
    )
}
