# Real-device testing checklist

Most of Warden is verified on an emulator (see commit history), but a few things can only be
confirmed on a physical phone with real apps and a real Quick Settings panel. Work through
these once on your device.

## Setup
1. `./gradlew installDebug` with the phone connected (see [SETUP.md](SETUP.md)).
2. Grant, in-app: **Accessibility** (Settings → Accessibility → Warden), **Usage access**,
   **Notifications**, and turn blocking **On** once (accept the VPN consent dialog).

## Must-verify on hardware

### 1. In-app feed detection (highest priority — signals need tuning)
The `viewId` signals in `FeatureCatalog.kt` are best-effort guesses; real app versions may
differ. For each feed you care about:
- Enable it in **Blocklist → Block in-app feeds**.
- Open the real app and navigate to the feed (e.g. Instagram → Reels).
- ✅ Expect the "off-limits" screen. ❌ If nothing happens, the signal needs updating.

**To capture the real signals** (with the phone connected):
```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
# open the feed first, then run the dump; grep the resource-ids:
grep -oE 'resource-id="[^"]*"' ui.xml | sort -u
```
Add the distinctive id fragment (e.g. `clips_viewer`) to that feature's `viewIdContains`.
Do this per app; expect to revisit when apps update.

### 2. Quick Settings tile
- Pull down the shade → edit (pencil) → drag the **Warden** tile into the active set.
- Tap it. ✅ Blocking toggles on/off (tile shows active/inactive).
- With a PIN set, tapping to turn **off** should open the app for the PIN — the tile must not
  bypass strict mode.
  *(Not verifiable on a headless emulator — `cmd statusbar click-tile` doesn't invoke it.)*

### 3. DNS-over-HTTPS reality check
- Block a site (e.g. `example.com`), turn blocking on, open it in **Chrome**.
- ✅ It should fail to load. ⚠️ If Chrome has "Secure DNS" (DoH) on, it may bypass the
  sinkhole — confirm whether your browser does, and note it. Mitigation (blocking known DoH
  endpoints) is on the roadmap.

### 4. Device-admin uninstall protection
- Settings → turn on **Uninstall protection**.
- Try to uninstall Warden from the launcher / system settings.
- ✅ Android should refuse until you turn protection off first.

### 5. Strict mode + PIN
- Set a PIN, enable strict mode, turn blocking on.
- ✅ Turning blocking off (app switch or tile) requires the PIN.

### 6. Reboot persistence
- With blocking on, reboot the phone.
- ✅ The DNS filter should re-arm automatically (BootReceiver).

## Report back
Note which feeds detected correctly (and paste any `resource-id`s that need adding), whether
DoH bypassed the block in your browser, and anything that didn't behave — those become the
next fixes.
