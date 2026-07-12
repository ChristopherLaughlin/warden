# Warden — Technical Spec

## Goal

A free, open-source Android app that does everything
[AppBlock](https://play.google.com/store/apps/details?id=cz.mobilesoft.appblock) does — block
apps and websites, on schedules and other conditions, with strict/PIN protection and usage
stats — and more, with a stronger privacy posture (local-only, no accounts, auditable).

## Non-negotiable principles

1. **Local-only.** No servers, no accounts, no analytics, no ad SDKs. The VPN never proxies
   traffic off-device; it only answers DNS. Accessibility reads only the foreground package
   name, never screen content.
2. **Auditable.** Every permission's use is visible in code. This matters because a blocker
   asks for VPN + accessibility, exactly the permissions malware wants.
3. **Hard to bypass, easy to leave.** Strict mode makes impulsive disabling hard, but the
   user is always ultimately in control (no dark patterns, clean uninstall when not in strict
   mode).

## Architecture

```
                        ┌────────────────────┐
   Compose UI  ───────► │   WardenViewModel   │
   (screens)            └─────────┬──────────┘
                                  │
                        ┌─────────▼──────────┐
                        │   AppContainer      │  (service locator; swap for Hilt later)
                        │  repo / settings /  │
                        │     blockEngine     │
                        └───┬────────────┬────┘
                            │            │
                 ┌──────────▼──┐   ┌─────▼────────────┐
                 │ BlockRepo   │   │  SettingsStore   │
                 │ (Room DAO)  │   │  (DataStore)     │
                 └─────────────┘   └──────────────────┘
                            ▲
        ┌───────────────────┼───────────────────────┐
        │                   │                        │
┌───────▼────────┐  ┌───────▼───────────┐   ┌────────▼─────────┐
│ WardenVpnService│  │ AppBlockA11yService│   │  BootReceiver    │
│ DNS sinkhole    │  │ foreground-app     │   │  re-arm on boot  │
│ (websites)      │  │ block (apps)       │   └──────────────────┘
└─────────────────┘  └────────────────────┘
```

### Key files
- `vpn/WardenVpnService.kt` — establishes a local TUN, routes only DNS into it, and either
  **sinkholes** (A → 0.0.0.0) a blocked domain or **forwards** the query to `1.1.1.1` and
  NATs the reply back. Adds itself to disallowed apps.
- `vpn/DnsPacket.kt` — minimal IPv4/UDP/DNS parsing + response building. Deliberately handles
  the common case; TCP-DNS/IPv6/EDNS are follow-ups (they fall through to the forwarder).
- `accessibility/AppBlockAccessibilityService.kt` — on `TYPE_WINDOW_STATE_CHANGED`, checks the
  package against the engine and launches `BlockedActivity`.
- `block/BlockEngine.kt` + `block/ScheduleEvaluator.kt` — the single source of truth for
  "is blocking active now, and for what?". Combines master switch, always-on flag, and
  active schedules.
- `data/` — Room (`BlockedItem`, `Schedule`) + DataStore settings (master/always-on/strict/PIN).
- `security/PinHasher.kt` — PBKDF2-HMAC-SHA256, salted; PIN never stored in plaintext.

### Blocking decision
`isBlockingActiveNow = masterEnabled && (alwaysOn || anyEnabledScheduleActiveNow())`.
The VPN calls `blockedDomains()`; the accessibility service calls `isPackageBlockedNow(pkg)`.

## Schedules
`Schedule` = name + `daysMask` (bit0=Mon…bit6=Sun) + start/end minute-of-day. Windows wrap
past midnight when `end <= start`. `ScheduleEvaluator` is pure and unit-testable.

## Data model migrations
Room `version = 1`, `exportSchema = false` for now. Before shipping, enable schema export and
write migrations — never ship destructive `fallbackToDestructiveMigration` to real users.

## Roadmap detail (parity + beyond)

### Parity with AppBlock
- **Full schedule editor**: multiple windows/day, per-day times, assign specific items to a
  profile (currently schedules gate the whole list).
- **Conditions engine**: extend `BlockEngine` with pluggable `Condition`s — time (done),
  Wi-Fi SSID, location geofence, app launch-count, daily usage-limit.
- **Strict mode enforcement**: while active, block the app's own disable path, hide the
  master toggle behind a timer/PIN, and use `DevicePolicyManager` (device admin) to make
  Warden non-uninstallable. Provide a clearly-documented emergency exit.
- **PIN lock**: gate settings/disable behind the PBKDF2 PIN already scaffolded.
- **Adult-content blocking**: ship curated category domain lists, toggle to merge into the
  sinkhole set. Keep lists in-repo and updatable.
- **Notification blocking**: `NotificationListenerService` to suppress notifications from
  blocked apps during active windows.
- **Usage stats**: expand `StatsScreen` with per-day history, charts, and "unlock attempts".
- **Quick block**: a Quick Settings tile + a home-screen "start focus session" (Pomodoro).

### Beyond AppBlock
- Encrypted local backup + optional **E2E-encrypted sync** across your own devices (no server
  can read the blocklist).
- Community-shared, signed blocklists (opt-in), like uBlock filter lists.
- On-device "why did I open this?" journaling to build awareness, fully local.

## Testing priorities
1. `ScheduleEvaluator` — pure logic, cover midnight-wrap and day boundaries.
2. `DnsPacket` — parse/build round-trips against captured DNS packets.
3. `BlockEngine` — decision table across master/always-on/schedule combinations.
4. Instrumented: VPN establish + sinkhole a known domain on an emulator.

## Known limitations of the v1 scaffold
- DNS filtering covers UDP/IPv4. Apps using DNS-over-HTTPS/TLS (e.g. Chrome's Secure DNS)
  bypass a DNS sinkhole — document this and consider blocking known DoH endpoints, or pair
  with the accessibility URL-reading fallback for browsers.
- Schedule UI is a quick-add stub; the model behind it is complete.
- Strict mode is a stored flag; enforcement is not wired yet.
