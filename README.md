# Realme Unlink

A standalone, local-only companion for **realme Buds Wireless 5 ANC**.

- No account, ads, cloud, `INTERNET`, or firmware OTA
- Material 3 Expressive + Google Sans Flex + dynamic color
- Capability-driven controls validated on-device
- **Quick Settings tile only** (no launcher icon) — pin “Realme Unlink” from the QS editor

## Features

- Battery (per reported bud/case)
- Noise control (Off / Transparent / Mild / Moderate / Strong / Smart)
- EQ profile selection + custom EQ editor with suggestions
- Multi-band bass
- Proven feature toggles (gaming collapsed to one control, voice enhancement, wind noise, dynamic sound)
- Gesture remapping
- In-ear wear status
- Multipoint status + enable
- Find earbuds tone
- Firmware version

## Connection model

Foreground-persistent RFCOMM: connect and refresh when the app is visible; disconnect on background. No always-on service.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/.local/share/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$HOME/.local/share/gradle/gradle-9.5.0/bin:$ANDROID_HOME/platform-tools:$PATH"
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open via Quick Settings → edit tiles → add **Realme Unlink**. Tap (or long-press) the tile to open the app.

## Diagnostics

Probe / evidence-matrix code remains in the binary for automation but is **not exposed in any UI**. Debug builds only:

```bash
adb shell am start -n app.realme.unlink/.MainActivity --ez run_evidence_matrix true
adb logcat -s UnlinkEvidence:I UnlinkEvidence:E
```

## License notes

Google Sans Flex is bundled under OFL-1.1; see `licenses/google-sans-flex-NOTICE.txt`.

See `HANDOFF.md` for protocol facts and the evidence matrix.
