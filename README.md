# Warden

**A free, open-source website & app blocker for Android.** No subscriptions, no ads, no
account, no data collection. A community-owned alternative to closed blockers like AppBlock.

> Working name — "Warden" is a placeholder and easy to rename (see [Renaming](#renaming)).

---

## Why

Most Android blockers gate their most useful features (strict mode, multiple schedules,
website blocking) behind a subscription. Warden makes the whole feature set free and the
code auditable — important for an app that, by design, needs deep device permissions.

## How blocking works

Warden uses **two independent layers**, so there's no single point of bypass:

| Layer | Mechanism | Blocks |
|-------|-----------|--------|
| **Website filtering** | A local, on-device `VpnService` that intercepts DNS and blackholes queries for blocked domains. Non-blocked queries are forwarded to an upstream resolver. | Websites in **every** browser and app |
| **App blocking** | An `AccessibilityService` detects when a blocked app comes to the foreground and shows a full-screen block screen. | Native apps |

The VPN is **local-only** — no traffic is proxied off-device, nothing is logged, and Warden
adds itself to the disallowed-apps list so it never filters its own traffic. See
[`SPEC.md`](SPEC.md) for the full architecture and privacy model.

## Feature status

**v1 (this scaffold — core is stubbed end-to-end and ready to build on):**
- [x] Website blocking via local DNS-filtering VPN
- [x] App blocking via accessibility service + block screen
- [x] Persistent blocklist (Room)
- [x] Master on/off with system VPN consent flow
- [x] Time-based schedules (data model + evaluator; quick-add UI)
- [x] Usage/screen-time dashboard (UsageStatsManager)
- [x] Strict-mode + PBKDF2 PIN hashing scaffolding
- [x] Restart-after-reboot

**Roadmap (matching AppBlock and going beyond — see `SPEC.md`):**
- [ ] Full schedule editor (per-day, multiple windows, per-item scope)
- [ ] PIN lock screen + strict-mode enforcement (device admin / uninstall protection)
- [ ] Conditions beyond time: Wi-Fi network, location, launch-count, usage limit
- [ ] Adult-content category blocking (curated domain lists)
- [ ] Notification blocking for blocked apps
- [ ] Quick-block tile + focus sessions (Pomodoro)
- [ ] Per-item schedule assignment
- [ ] Import/export & optional end-to-end encrypted sync

## Building

You'll need the **JDK 17** and the **Android SDK** (both easiest via Android Studio).

```bash
# 1. Install Android Studio (bundles JDK + SDK):
#    https://developer.android.com/studio
# 2. Open this folder in Android Studio — it will sync Gradle and write local.properties.
# 3. Or from the command line, once ANDROID_HOME points at your SDK:
./gradlew assembleDebug
./gradlew installDebug   # to a connected device / emulator
```

> This machine currently has no JDK or Android SDK installed — install Android Studio first.

### Required permissions (all granted by the user, none abused)
- **VPN consent** — for the local DNS filter (system dialog on first enable).
- **Accessibility** — to detect the foreground app. Warden reads only the package name.
- **Usage access** — for the screen-time dashboard.
- **Notifications** — for the ongoing "blocking active" notice (required for a foreground service).

## Renaming

Everything keys off `com.warden.blocker`. To rebrand: change `applicationId`/`namespace` in
[`app/build.gradle.kts`](app/build.gradle.kts), the package folders, `rootProject.name` in
[`settings.gradle.kts`](settings.gradle.kts), and `app_name` in `res/values/strings.xml`.
Android Studio's *Refactor → Rename* handles the package move cleanly.

## License

[GPL-3.0](LICENSE) — Warden is free software; derivatives must stay free too.

## Contributing

Issues and PRs welcome. Please read `SPEC.md` first for the architecture and the privacy
constraints that any change must preserve.
