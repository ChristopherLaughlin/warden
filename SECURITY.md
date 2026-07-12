# Security & Privacy

Warden holds four of the most powerful — and most-abused — permissions on Android
(VpnService, AccessibilityService, DeviceAdmin, NotificationListener). That combination is
exactly the profile malware uses, so Warden is built to *prove* it is benign and local-only.
This document is the threat model and the measures in place.

## Core principle: local-only, least privilege

- **Nothing leaves your device.** No servers, no accounts, no analytics, no ads, no tracking
  SDKs. The VPN is local; it does not proxy or forward your traffic to Warden or anyone else.
- Every permission's use is visible in this open-source code and justified below.

## Threat model

| Adversary | Goal | Mitigation |
|-----------|------|------------|
| Impulsive self (the user) | Turn blocking off in a weak moment | PIN gate, strict mode, device-admin uninstall lock, focus sessions |
| Thief / shoulder-surfer | Read the PIN, disable protection | PBKDF2 (310k) + salt, lockout, FLAG_SECURE, tapjacking guards |
| Malicious app on device | Trick user into disabling; capture PIN via overlay | `filterTouchesWhenObscured` + `setHideOverlayWindows` on intercept/PIN |
| Attacker with adb / backup | Extract PIN hash or blocklist | `allowBackup=false`; PIN stored only as salted hash |
| Network attacker | MITM Warden's traffic | Warden makes no HTTP calls; cleartext forbidden |
| Hostile packets in the tunnel | Crash/exploit the packet loop | Defensive bounds-checked parsing; whole loop is exception-guarded |

## What each permission does — and doesn't

- **VpnService** — a *local* tunnel that filters DNS on-device. Blocked domains are answered
  with a dead-end; **allowed lookups are forwarded to the device's own DNS resolver**
  (captured from the active network), never to a resolver of Warden's choosing. Warden never
  reads, stores, or transmits your browsing. Queried domains are **never logged**.
- **AccessibilityService** — reads the foreground package name (to block apps) and, for
  in-app feed blocking, scans on-screen view-ids/labels of the current app. It **never logs,
  stores, or transmits screen content** — it makes an in-memory block/allow decision and
  discards. Critical apps (launcher, settings, dialer, phone, Warden itself) are never
  intercepted, so you can't be locked out of your own device.
- **DeviceAdmin** — declares **no enforcement policies**; active-admin status alone provides
  uninstall protection. Disable it anytime from Settings (behind your PIN) — Warden never
  traps you.
- **NotificationListener** — opt-in; reads only the posting package name to cancel
  notifications from blocked apps. Notification **content is never read, stored, or sent**.

## Hardening measures (code)

- **PIN**: `PBKDF2WithHmacSHA256`, 310,000 iterations, per-PIN `SecureRandom` salt; stored as
  hash only. Compounding lockout after 5 failures (30s → 1h). Constant-time comparison.
- **Screen protection**: `FLAG_SECURE` on the PIN pad (release builds) blocks
  screenshots/recording; `filterTouchesWhenObscured` + `setHideOverlayWindows` on the PIN and
  the intercept screen defeat tapjacking overlays.
- **Network**: `network_security_config.xml` with `cleartextTrafficPermitted=false` and
  `usesCleartextTraffic=false`. Warden makes no outbound HTTP(S) requests of its own.
- **Backups**: `android:allowBackup="false"` — the PIN hash and blocklist can't be pulled via
  `adb backup` or cloud backup.
- **Exported surface**: only the launcher activity is exported. The intercept activity is
  `exported=false`; the VPN/accessibility/tile/notification/device-admin services are
  system-bound and protected by their respective `BIND_*` permissions.
- **Release builds**: R8 shrinks + obfuscates and **strips all `android.util.Log`** calls, so
  no diagnostic logging can leak in production.
- **Packet handling**: `DnsPacket` bounds-checks every offset before reading and drops
  malformed packets; the packet loop is fully exception-guarded so hostile input can't crash
  the app.

## Known limitations (honest)

- **DNS-over-HTTPS/TLS** can bypass the DNS sinkhole. Warden optionally sinkholes known DoH
  resolver hostnames (Settings → Block DNS-over-HTTPS) to close the common case, but clients
  with hardcoded resolver IPs can still slip through.
- **IPv6 DNS** is not yet routed into the tunnel (only the virtual IPv4 resolver is). Tracked
  for a future release.
- **Clock changes** can affect time-based blocking (schedules/focus). Hardening the focus
  timer against clock changes (elapsed-realtime) is planned.
- The PIN is not yet bound to the hardware Keystore/StrongBox — planned as defense-in-depth
  against offline extraction on rooted devices (the salted 310k-PBKDF2 hash + no-backup is the
  current protection).

## Reporting a vulnerability

Please open a **private** security advisory on the GitHub repository (Security → Report a
vulnerability) rather than a public issue. We aim to acknowledge within a few days.
