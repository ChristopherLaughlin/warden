# Setup & Build

Warden is a standard Gradle/Android project. You need **JDK 17** and the **Android SDK
(API 34)**. Two paths below: the quick command-line path (already bootstrapped on this Mac)
and the Android Studio path (best for running on a device/emulator).

## This machine is already set up

A no-sudo toolchain was installed for you:

- **JDK 17** via Homebrew: `/opt/homebrew/opt/openjdk@17`
- **Android SDK** at `~/Library/Android/sdk` (platform-tools, platform 34, build-tools 34.0.0)
- `local.properties` already points Gradle at that SDK.

Build from a terminal:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
cd ~/warden

./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # runs the JVM unit tests (schedule logic, etc.)
```

To make the env vars permanent, add those two `export` lines to `~/.zshrc`.

## Running on your phone

Warden's whole job needs a real device or emulator with Google-style browsers.

1. Enable **Developer options** on the phone (tap *Settings → About phone → Build number* 7×),
   then turn on **USB debugging**.
2. Plug in over USB and authorize the computer.
3. Install and launch:
   ```bash
   export PATH=$ANDROID_HOME/platform-tools:$PATH
   adb devices                 # confirm your phone shows up
   ./gradlew installDebug      # builds + installs
   adb shell monkey -p com.warden.blocker 1   # launches it
   ```

### First-run permissions (grant these in-app / when prompted)
- **VPN consent** — appears when you first flip blocking on (local DNS filter).
- **Accessibility** → *Settings → Accessibility → Warden* → On (app blocking + mindful pause).
- **Usage access** — Settings screen button (screen-time + daily time limits).
- **Notifications** — allow the ongoing "blocking active" notice.
- **Uninstall protection** (optional) — Settings → turn on device admin.

## Android Studio (recommended for UI work)

1. Install [Android Studio](https://developer.android.com/studio) (bundles JDK + SDK).
2. **File → Open** → select `~/warden`. Let it sync Gradle (it rewrites `local.properties`
   to its own SDK automatically).
3. Pick a device (physical or create an emulator via *Device Manager*, API 34+) and hit **Run**.

## Fresh machine, command-line only

If you ever set this up elsewhere without Android Studio:

```bash
brew install openjdk@17
# Android cmdline tools:
mkdir -p ~/Library/Android/sdk/cmdline-tools
curl -fsSL https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip -o /tmp/cmdline.zip
unzip -q /tmp/cmdline.zip -d /tmp/clt && mv /tmp/clt/cmdline-tools ~/Library/Android/sdk/cmdline-tools/latest
export ANDROID_HOME=~/Library/Android/sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > ~/warden/local.properties
```

## Troubleshooting
- **`Unable to locate a Java Runtime`** → `JAVA_HOME` isn't set to the JDK 17 path above.
- **`SDK location not found`** → `local.properties` is missing/wrong; re-run the last line above,
  or open once in Android Studio.
- **Gradle re-downloads every build** → expected only on the *first* build; the wrapper caches
  the distribution under `~/.gradle` afterward.
