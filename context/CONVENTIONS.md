# Code Conventions

## Platform

- Target Android 17 / API 37 only.
- Match only `realme Buds Wireless 5 ANC`.
- Keep `INTERNET`, foreground-service, boot, and wake-lock permissions absent.
- Keep the Quick Settings tile as the sole production entrypoint.

## Verification

```bash
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
npx --yes markdownlint-cli2
git diff --check
```

## Hardware Safety

- Obtain explicit confirmation before using ADB or shared hardware.
- Establish baseline, perform one reversible mutation, read back, restore immediately, and read back restoration.
- Stop on ambiguous state or failed restoration.
- Use official-app writes plus GET-only diagnostics for correlation.
- Store raw captures in `.pi/artifacts/`; keep todo evidence compact.

## Bluetooth

- Run socket I/O on `Dispatchers.IO`.
- Serialize session operations.
- Match responses by command and transfer ID.
- Close the socket to cancel blocked reads.
- Treat transport errors as session failures and decode errors as per-feature failures.
- Bind sessions to foreground lifecycle generation ownership.
- Stop active Find neckband mode before teardown.
- Use `0x012C`/`0x041B` for Dynamic bass bands; omit `0x0124`.

## Writes

- Preserve device-reported schemas and ranges.
- Gate controls on successful reads and advertised capabilities.
- Apply optimistic state with error rollback.
- Compensate multi-command Custom EQ failures back to the prior profile.
- Use switch ID `6` for Game mode, `8` for Auto answer, `26` for Smart de-wind, `27` for Spatial Audio, and `29` for Dynamic bass.
- Offer MFB functions `0`, `1`, `4`, `5`, `6`, and `17` only.
- Use `0x0110`/`0x0407` for Volume enhancer.
- Use `0x010C`/`0x0404` action 2 for ANC-button mode-list cycling.

## Compose

- Use Material 3 Expressive with dynamic color and bundled Google Sans Flex throughout.
- Keep primary actions direct; use sheets for secondary configuration.
- Use one shared MFB action picker rather than repeating choices per gesture.
- Keep device identity, connection, and battery in one header.
- Use primary, secondary, and tertiary roles as restrained semantic accents.
- Render unavailable state instead of fabricating fallback hardware data.

## Diagnostics

- Keep exploratory and write-capable probes under `src/debug`.
- Keep the release diagnostics bridge a no-op.
- Separate GET-only diagnostics from write-capable probes.
- Reference files as `path:line[:column]`.
