# realme Unlink — handoff

Updated: 2026-08-03

## Mission and product boundary

Build a native, standalone companion for **realme Buds Wireless 5 ANC**, targeting Pixel 10 / Android 17 (API 37). The intended product is a polished Material 3 Expressive, capability-driven app with no account, ads, upsells, cloud dependency, `INTERNET` permission, or firmware-update flow.

The current app is still a protocol/UX diagnostic. Its dashboard is not the final product UI: it exposes raw protocol traces and diagnostic actions. Do **not** reshape the main/final UI without first confirming product decisions with the user.

## Current status

**Product UI v1 is implemented** (2026-08-03 continuation): QS-tile-only entry, foreground-persistent session, full evidence-backed dashboard, no diagnostics UI.

The direct feasibility gate and the live feature-evidence matrix both passed on the bonded headset with realme Link stopped.

Protocol / transport:

- Bluetooth Classic RFCOMM connection through the vendor control UUID works.
- OPPOv1 transport framing, incoming fragment reassembly, and outbound first/middle/final fragmentation work.
- Capability parsing validates status `0`, retains the raw bitmap, uses LSB-first bit numbering, maps known groups, and preserves unknown bits.
- `HeadsetSession` serializes transactions, matches by base command + transfer ID, ignores unsolicited frames, times out, cancels, and handles disconnect/rollover.

Already-validated before this pass:

- Capability, battery, firmware/version, noise-state, button mappings, wear status, switch-feature status, EQ status, multipoint status, EQ profiles, and multi-band bass reads.
- Direct `0x0124` bass-engine reads timed out on two fresh reconnects and remain excluded from discovery.
- Idempotent EQ-status, switch-feature 8, and full button-mapping writes.
- Reversible EQ profile selection round-trip (`0 → profile 4 → 0`).
- `0x0115` hearing-enhancement data returned failure status; spatial audio command `0x041E` was unsupported.

Live evidence matrix (2026-08-03, this session):

- Noise mode writes for action/type `1/1` values `1, 2, 4, 16, 32` were each accepted, read back, and restored to baseline `8` (strong noise reduction).
- Switch-feature reversible toggles accepted for IDs `6, 8, 9, 17, 26, 29, 38` and restored. Rejected with status `1`: `4, 5, 7, 11, 12, 19, 24, 54`. Feature `27` (spatial) was not written because `0x041E` is unsupported.
- Button remap: single-press play/pause → next track accepted and read back; original mappings restored. Earlier volume-up → volume-down on the secondary button group was accepted but not applied.
- Custom EQ modify (`0x0418` action `2`) on profile `4` / `Custom1` changed band 0 `0 → 1`, read back, restored bands and EQ ID `0`. Device-reported frequencies were `50, 250, 1000, 4000, 8000, 16000` (preserve device values; do not substitute reference defaults).
- Multi-band bass write (`0x041B`) changed type `1` `0 → 1`, read back via `0x012C`, restored all three bands.
- Wear snapshot reconfirmed left/right flags `1`, case flags `4` (read-only; physical transitions not automated).
- Multipoint snapshot reconfirmed connected / this phone / `Pixel 10`. Feature `17` reversible toggle succeeded. No `0x040B` connect/delete action was exercised.
- Find-mode action `0x0400` start and stop both accepted. Find-device mode switch feature `38` reversible toggle also succeeded.

Build:

- Gradle unit tests and debug APK build pass.
- Unit coverage now includes custom-EQ and bass write payload encoding in addition to framing, parsers, semantics, session matching, fragmentation, rollover, timeout, cancellation, and disconnect handling.

The repository intentionally has no Git metadata. Do not initialize Git unless requested.

## Latest continuation findings (2026-08-03)

### Read-only baseline (earlier same day)

- Snapshot: battery around `90%`, firmware `1.1.0.64`, current EQ ID `0`, multipoint state `2` / flag `1` / name `Pixel 10`. No multipoint address retained.
- Noise baseline `action=1, type=1, value=8` → strong noise reduction.
- Wear: left/right flags `1`; case flags `4`.
- Button mappings: single → play/pause; double → device change; triple → noise reduction; long → previous track; secondary group single → volume up.
- Switch-feature status: `7=off`, `6=off`, `4=off`, `5=off`, `11=off`, `8=on`, `17=off`, `19=off`, `24=off`, `9=off`, `26=off`, `12=off`, `29=off`, `27=on`, `54=off`, `38=on`.

### Evidence matrix results (this session)

| Family | Result | Notes |
|---|---|---|
| Noise modes | **Validated** | action/type `1/1` values `1,2,4,8,16,32` all work; restored to `8` |
| EQ profile selection | **Validated** | earlier `0 → 4 → 0` round-trip |
| Custom EQ bands | **Validated** | `0x0418` modify/restore on profile 4 |
| Multi-band bass | **Validated** | `0x041B` write + `0x012C` read-back/restore |
| Direct bass `0x0124` | **Excluded** | timed out twice earlier; do not retry in discovery |
| Switch toggles | **Partially validated** | accepted `6,8,9,17,26,29,38`; rejected `4,5,7,11,12,19,24,54`; skip `27` writes |
| Button remapping | **Validated** | media-function remap works; volume-function remap on secondary button not applied |
| Wear state | **Read validated** | snapshot only; no write path; live physical transitions not automated |
| Multipoint status | **Read validated** | status + feature `17` toggle validated; `0x040B` not exercised |
| Find-device | **Validated** | feature `38` toggle + `0x0400` start/stop accepted |
| Spatial audio | **Out of scope** | `0x041E` unsupported despite feature `27=on` |
| Hearing enhancement | **Not usable** | `0x0115` failure status |

### Explicit handoff stop point

Do **not** start implementing or reshaping the main/final UI from this handoff without first confirming the product decisions with the user. Settle:

- the v1 feature list (now that the evidence matrix is complete);
- refresh-only versus persistent/live connection behavior;
- device-not-bonded, Bluetooth-off, permission-denied, out-of-range, and realme Link contention states;
- which diagnostics, if any, remain behind a developer-only surface.

Protocol/evidence work may continue independently of the final UI.

## Agreed v1 boundary

Deferred reads remain excluded from v1 and are only recorded for possible future support:

- `0x0107`: firmware/upgrade capability
- `0x0110` / `0x0407`: high-volume enhancement
- `0x0120`: voice-prompt language

Do not query, write, or expose those paths in v1. Firmware/OTA, spatial audio, accounts, ads, cloud services, debug mode, and event registration are also out of scope. Do not infer a feature merely because its capability bit is set.

Direct bass-engine command `0x0124` remains excluded after repeated timeouts.

Evidence-backed candidates now ready for product/UI discussion (not automatically committed to UI):

- Connection / refresh status
- Battery
- Firmware version in device details
- Mapped noise-control card with writes for action/type `1/1`
- EQ profile selection
- Custom EQ band editing for device-advertised custom profiles
- Multi-band bass controls via `0x012C`/`0x041B`
- Switch toggles only for IDs proven accepted: `6, 8, 9, 17, 26, 29, 38`
- Button remapping for media functions that read back; do not assume every function/button pair is writable
- Wear-state read-only card
- Multipoint read-only status; optional multipoint enable via feature `17`. Do not expose `0x040B` connect/delete without a dedicated address-safe design and explicit approval
- Find-device via `0x0400` and/or feature `38`

Rejected switch IDs must not be exposed as working toggles: `4, 5, 7, 11, 12, 19, 24, 54`.

## Protocol facts

Transport:

- RFCOMM PSM 3.
- Vendor UUID: `0000079A-D102-11E1-9B23-00025B00A5A5`.
- SPP fallback: `00001101-0000-1000-8000-00805F9B34FB`.
- Android `BluetoothSocket` handles the lower RFCOMM multiplexer/credits layer.

OPPOv1 envelope:

```text
0xAA | 7-bit length varint | link data
link data = control byte | reserved byte | TL packet
```

Control values `1`, `2`, and `3` are first, middle, and final fragments. Incoming reassembly and outbound fragmentation are implemented in `HeadsetProtocol.kt`; `wrapFrames()` uses the vendor 2000-byte complete-frame limit, while `wrap()` remains a single-frame convenience method for small packets.

TL packet:

```text
uint16 little-endian command
uint8 transfer/sequence ID
uint16 little-endian payload length
payload bytes
```

Responses set bit `0x8000`. `HeadsetSession` matches by base command and transfer ID, ignores unsolicited frames, serializes transactions, and closes the socket to cancel blocked reads.

Confirmed commands:

| Command | Purpose |
|---:|---|
| `0x0100` | capability query |
| `0x0105` | firmware/version read |
| `0x0106` | battery read |
| `0x0108` | button mappings read |
| `0x0109` | wear-status read |
| `0x010C` | noise-state read; this model uses selector `01 01` |
| `0x010D` | switch-feature status read |
| `0x010F` | current EQ ID read |
| `0x0112` | multipoint status read |
| `0x0115` | hearing-enhancement read; rejected by this device |
| `0x0122` | EQ profiles/custom EQ data read |
| `0x0124` | bass-engine read; timed out on two fresh reconnects and excluded from v1 |
| `0x012C` | multi-band bass data read |
| `0x0400` | find-mode action write; start/stop accepted |
| `0x0401` | button mappings write |
| `0x0403` | switch-feature write |
| `0x0404` | noise-control write |
| `0x0406` | EQ-status/profile write |
| `0x0418` | custom EQ add/modify/delete write; modify validated |
| `0x041B` | bass-engine write; multi-band typed payload validated |

Custom EQ write payload:

```text
action(1=add,2=modify,3=delete) | min | max | id | nameLen | name UTF-8 |
bandCount | (freq LE16 | signed dB8)*
```

Bass write payload:

```text
min | max | current [| type when type > 0]
```

The successful capability response was bitmap `BF1FDA2045044000`, with set bits:

```text
0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12,
17, 19, 20, 22, 23, 29, 32, 34, 38, 42, 54
```

Known mapped groups are retained in `HeadsetCapabilities.kt`; unknown set bits are `12, 17, 20, 42`. A capability bit advertises a family, not proof that every subcommand or subfeature is usable.

Noise action `1` values confirmed on this device:

| Value | Label |
|---:|---|
| 1 | Normal / off |
| 2 | Transparency |
| 4 | Mild noise reduction |
| 8 | Strong noise reduction |
| 16 | Moderate noise reduction |
| 32 | Smart noise reduction |

Switch feature IDs with live write evidence:

| ID | Label | Live write |
|---:|---|---|
| 4 | unknown/raw | rejected |
| 5 | Power saving | rejected |
| 6 | Gaming mode | accepted + restored |
| 7 | Party mode | rejected |
| 8 | Gaming mode | accepted + restored |
| 9 | Voice enhancement | accepted + restored |
| 11 | HearID | rejected |
| 12 | Voice enhancement | rejected |
| 17 | Multipoint | accepted + restored |
| 19 | AI microphone | rejected |
| 24 | High-quality audio | rejected |
| 26 | Wind-noise control | accepted + restored |
| 27 | Spatial audio | do not write; `0x041E` unsupported |
| 29 | Dynamic sound | accepted + restored |
| 38 | Find-device mode | accepted + restored |
| 54 | Virtual bass | rejected |

## Work remaining before product/UI lock

### 1. Finish the capability and semantics matrix — complete

- `0x0124` remains an explicit exclusion.
- Semantic labels live in `HeadsetSemantics.kt` and are used only where confirmed; unknown values retain raw numbers.
- Action/registration/debug commands remain separate from safe reads.

### 2. Finish validation for features needing evidence — complete

Live reversible validation now covers:

- Noise mode mapping for action/type `1/1`
- EQ profile selection
- Custom EQ modify/restore
- Multi-band bass write/restore
- Per-feature switch toggles (accepted vs rejected list above)
- Real button remap with restore
- Find-mode action start/stop
- Wear and multipoint read snapshots

Still intentionally not exercised:

- Multipoint per-device action `0x040B` (address-sensitive connect/delete)
- Custom EQ add/delete actions
- Physical wear transitions
- Any deferred/out-of-scope command family

### 3. Harden the protocol layer before large writes — complete

- Outbound OPPOv1 fragmentation implemented and tested.
- Custom EQ and bass writers added with unit-tested payload encoding.
- Evidence probe restores baselines after temporary changes.

### 4. Then decide the product and final UI

The final UI should remove raw protocol traces and diagnostic controls from the main surface. Candidate structure: connection/refresh header, battery card, mapped noise control, EQ/custom EQ, bass, validated switch toggles, button remap, optional wear/multipoint/find-device cards.

Before implementation of the final UI, settle with the user:

- the v1 feature list from the evidence-backed candidates above;
- refresh-only versus persistent/live connection behavior;
- device-not-bonded, Bluetooth-off, permission-denied, out-of-range, and realme Link contention states;
- which diagnostics, if any, remain behind a developer-only surface.

A feature is ready for UI commitment only when it has a validated capability advertisement, parser, user-facing semantic mapping, and validated write/read-back behavior when writable. That bar is now met for the evidence-backed candidates listed above.

## Key files

- `app/src/main/java/app/realme/unlink/HeadsetProtocol.kt` — framing, parsers, custom-EQ/bass payload builders
- `app/src/main/java/app/realme/unlink/HeadsetSession.kt` — serialized RFCOMM command session and typed setters
- `app/src/main/java/app/realme/unlink/HeadsetCapabilities.kt` — capability bitmap model
- `app/src/main/java/app/realme/unlink/HeadsetCapabilityDiscovery.kt` — read-only query inventory/parsers
- `app/src/main/java/app/realme/unlink/HeadsetSemantics.kt` — confirmed user-facing value mappings with raw-value fallback
- `app/src/main/java/app/realme/unlink/HeadsetProbe.kt` — original diagnostic orchestration
- `app/src/main/java/app/realme/unlink/HeadsetEvidenceProbe.kt` — full reversible evidence matrix
- `app/src/main/java/app/realme/unlink/MainActivity.kt` — temporary diagnostic UI; supports `run_evidence_matrix` intent extra
- `app/src/test/java/app/realme/unlink/` — protocol/parser/session tests

## Build and validation

```bash
cd /home/kirubhas/src/realme-unlink
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/.local/share/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$HOME/.local/share/gradle/gradle-9.5.0/bin:$ANDROID_HOME/platform-tools:$PATH"
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Live evidence matrix:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.realme.link
adb shell am start -n app.realme.unlink/.MainActivity --ez run_evidence_matrix true
adb logcat -s UnlinkEvidence:I UnlinkEvidence:E
```

The headset must be awake, in range, and connected before live retries. Stop realme Link first. Do not unpair/reset the headset. Do not send writes without user approval and a fresh read, except the existing diagnostic/evidence actions that restore baselines.

## External references

Reverse-engineering material remains outside this repository:

- `/tmp/realme-link-jadx/`
- `/tmp/btmon-last.txt`
- `/tmp/gadgetbridge-codeberg/`
- `/tmp/gadgetbridge-feasibility/`

Do not copy decompiled source, extracted APKs, firmware, account data, MAC addresses, or other device identifiers into this project.
