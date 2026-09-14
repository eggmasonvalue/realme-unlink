# Realme Unlink

A native, standalone, and privacy-respecting companion app for the **realme Buds Wireless 5 ANC** (Model RMA2412) neckband headset, targeting Pixel 10 and Android 17 (API 37).

- **100% Local & Offline:** No accounts, no cloud dependencies, no ads, no telemetry, and zero `INTERNET` permission in `AndroidManifest.xml`.
- **Material 3 Expressive:** Styled with dynamic color theming, Google Sans Flex typography, and clean, non-duplicated status cards.
- **Quick Settings Tile Entry:** Clean home screen footprint with no launcher icon; access instantly via the "Realme Unlink" Quick Settings tile.
- **Empirically Validated:** Hardware controls and protocol payloads verified directly on physical hardware.

---

## Features

- **Noise Control:** Instant toggles for Strong ANC, Mild ANC, Smart ANC, Transparency, and Off, plus Wind Noise Reduction.
- **Equalizer & Custom EQ:** Switch between hardware presets (`Original`, `Serenade`, `Deep Bass`, `Clear Bass`) or tune a custom 6-band EQ (`50Hz`, `250Hz`, `1kHz`, `4kHz`, `8kHz`, `16kHz`) with preset suggestions.
- **Bass Tuner:** Multi-band level adjustments across low, mid, and high bands, plus Dynamic Bass Boost toggle.
- **Game Mode:** Ultra-low latency audio sync (45ms) toggle with aliased protocol support.
- **Hardware Remote Remapping:** Configure Multi-Function Button (MFB) single press, double press, triple press, and long press actions.
- **Dual Connection (Multipoint):** View active connected host devices and toggle multipoint audio.
- **Spatial Audio Integration:** Direct access to Android OS spatializer and Bluetooth system settings.
- **Find Device & Ring Phone:** Trigger an acoustic location tone on the neckband or respond to earbud button events to ring the phone.
- **Battery & Status Details:** Glanceable battery level, connection state pill, and firmware version.

---

## Connection Model

The application uses a **foreground-persistent RFCOMM connection model**:

- Connects automatically over Bluetooth Classic RFCOMM (vendor control UUID / PSM 3) when the app enters the foreground.
- Synchronizes device snapshot state without blocking the UI thread.
- Disconnects cleanly upon backgrounding to preserve phone and headset battery life.
- Requires no ongoing background services or persistent notification drawer clutter.

---

## Build & Test

### Prerequisites

Set up the required Java and Android SDK environment variables:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/.local/share/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$HOME/.local/share/gradle/gradle-9.5.0/bin:$ANDROID_HOME/platform-tools:$PATH"
```

### Build Commands

```bash
# Compile Kotlin debug
./gradlew compileDebugKotlin

# Assemble debug APK
./gradlew :app:assembleDebug

# Run unit test suites
./gradlew testDebugUnitTest

# Run code and markdown linters
./gradlew :app:lintDebug
npx --yes markdownlint-cli2
```

The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Installation & Usage

1. Install the APK to your paired Android device:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open the Android Quick Settings shade, tap the edit (pencil) icon, and drag the **Realme Unlink** tile into your active tiles.
3. Tap or long-press the tile to launch the app.
4. Separate the magnetic earbud clasps on the neckband to power it on; the app will connect and display the dashboard automatically.

---

## Diagnostics & Empirical Probes

Automated diagnostic probe suites remain included in debug builds for protocol validation but are completely excluded from release binaries and user UI surfaces:

```bash
# Run reversible feature-evidence matrix
adb shell am start -n app.realme.unlink/.MainActivity --ez run_evidence_matrix true
adb logcat -s UnlinkEvidence:I UnlinkEvidence:E

# Run button mapping persistence probe
adb shell am start -n app.realme.unlink/.MainActivity --ez run_button_test true
adb logcat -s UnlinkButtonTest:I UnlinkButtonTest:E
```

---

## Architecture & Project Documentation

For development workflows, architecture maps, and recorded design decisions, consult the project documentation system:

- [AGENTS.md](AGENTS.md) — Universal entry point, agent guardrails, and decision criteria.
- [context/MAP.md](context/MAP.md) — System architecture, sequence diagrams, and module index.
- [context/DECISIONS.md](context/DECISIONS.md) — Curated Architecture Decision Records (ADRs).
- [context/CONVENTIONS.md](context/CONVENTIONS.md) — Terse imperative code rules and commands.
