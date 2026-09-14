# Code Conventions (`context/CONVENTIONS.md`)

## Environment Setup

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/.local/share/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$HOME/.local/share/gradle/gradle-9.5.0/bin:$ANDROID_HOME/platform-tools:$PATH"
```

---

## Build, Test, and Lint Commands

- Compile Kotlin debug: `./gradlew compileDebugKotlin`
- Assemble debug APK: `./gradlew :app:assembleDebug`
- Run unit tests: `./gradlew testDebugUnitTest`
- Run Android Lint: `./gradlew :app:lintDebug`
- Run Markdown Lint: `npx --yes markdownlint-cli2`

---

## Hardware & Safety Rules

- Never run `adb` commands without explicit user instruction and confirmation that no other session is testing.
- Never issue unvalidated or blind writes to headset hardware.
- Implement read-back verification and baseline restoration for all hardware mutation tests.
- Support only confirmed writable switch IDs (`6, 8, 9, 17, 26, 27, 29, 38`).
- Keep `INTERNET` permission out of `AndroidManifest.xml`.

---

## Threading & Bluetooth Concurrency

- Run all Bluetooth socket I/O strictly on `Dispatchers.IO`. Never perform socket reads or writes on the Android Main (UI) looper.
- Guard all session writes with optimistic local updates and error-triggered rollbacks.
- Cancel blocked socket reads by closing the underlying `BluetoothSocket`.
- Bind session connections strictly to the foreground lifecycle (`onStart`/`onStop`). Disconnect RFCOMM cleanly upon backgrounding.

---

## Protocol & Wire Format

- Encode packet framing with prefix `0xAA`, 7-bit varint length, control byte, reserved byte `0x00`, and little-endian TL packet.
- Check the high bit (`command | 0x8000`) and matching transfer ID for response frames.
- Ignore unsolicited frames (`0x0501`) during synchronous request-response transaction matching.
- Route unsolicited `0x0501` frames containing payload byte `10` (`0x0A`) to the ring phone alarm handler.
- Use multi-band bass commands (`0x012C` read, `0x041B` write); never send direct bass command `0x0124`.

---

## Compose & UI

- Use Material 3 Expressive components (`ExperimentalMaterial3ExpressiveApi`).
- Set custom font family to `GoogleSansFlex`.
- Never duplicate device title, connection status, or battery indicators in multiple cards.
- Render hardware controls according to neckband physical button semantics (MFB, Volume, ANC key).
- Use `path:line[:column]` format for all file references in comments and logs.
