# Architecture Decisions

## Bluetooth Classic clean-room transport

**Context:** RMA2412 vendor controls use Bluetooth Classic RFCOMM and OPPOv1-framed TL packets. The official companion brings account, network, and telemetry dependencies.

**Decision:** Own a local RFCOMM implementation using vendor UUID `0000079A-D102-11E1-9B23-00025B00A5A5`, with standard SPP UUID fallback. Keep the app account-free and omit `INTERNET` permission.

**Tradeoff:** A feature is unavailable until its command and restoration behavior are validated on the target hardware.

**Status:** Active.

## Foreground-only control session

**Context:** Always-available headset events require a persistent process, control socket, foreground service, and notification.

**Decision:** Connect in `onStart`, disconnect in `onStop`, and launch through the Quick Settings tile. Do not implement background telemetry or Find My Phone until its trigger and lifecycle requirements are known.

**Tradeoff:** Controls and state refresh require the app to be visible; no persistent app-owned radio or CPU footprint remains after backgrounding.

**Status:** Active.

## Pixel 10 / Android 17 and exact RMA2412 identity

**Context:** The project has one target device and no compatibility user base. Similar product names do not establish protocol compatibility.

**Decision:** Set minimum and target SDK to API 37 and accept only the exact Bluetooth name `realme Buds Wireless 5 ANC`. Preserve device-reported schemas and ranges in writes.

**Tradeoff:** Older Android versions, renamed devices, and related headset models are unsupported until separately validated.

**Status:** Active.

## Quick Settings tile as the sole entrypoint

**Context:** The app is a secondary hardware-control surface rather than a general launcher destination.

**Decision:** Keep `MainActivity` internal without a launcher intent filter. Launch from `UnlinkTileService`; Android owns tile long-press behavior.

**Tradeoff:** Initial setup requires adding the tile from Android's Quick Settings editor.

**Status:** Active.

## Serialized optimistic writes with rollback

**Context:** RFCOMM transactions block and can fail after the UI has accepted input. Concurrent lifecycle and write work can corrupt visible state or close a newer session.

**Decision:** Serialize transport operations on `Dispatchers.IO`, guard lifecycle work with generation ownership, update snapshots optimistically, and restore the previous snapshot after rejected writes. Multi-command Custom EQ writes compensate back to the prior profile on partial failure.

**Tradeoff:** A failed operation can briefly show its requested value before rollback.

**Status:** Active.

## Compact single-surface interface

**Context:** Card-per-feature layouts and repeated action catalogs obscured common controls and increased interaction cost.

**Decision:** Use one compact Pixel-style settings surface with Google Sans Flex and dynamic Material color. Keep noise mode directly one-tap; place ANC tuning, EQ, Dynamic Audio, and button assignment details in focused modal sheets. Render one shared action picker for all MFB gestures. Use secondary and tertiary color roles for semantic accents rather than additional containers.

**Tradeoff:** Secondary configuration requires opening a sheet, while common mode switching and binary controls remain direct.

**Status:** Active.

## Evidence-gated controls

**Context:** Capability advertisement, accepted write status, visible official-app state, and persisted readback are distinct levels of evidence.

**Decision:** Expose a writable control only when its identity and state representation are confirmed. Treat malformed optional reads as per-feature unavailability and transport failures as session failures. Keep probes in debug source sets and exclude them from release binaries.

Confirmed values:

- Noise: Off `1`, Transparency `2`, Mild `4`, Max `8`, Moderate `16`, Smart `32`.
- MFB functions: None `0`, Play/Pause `1`, Voice Assistant `4`, Previous `5`, Next `6`, Game mode `17`.
- Switches: Game mode `6`, Auto answer `8`, Smart de-wind `26`, Dynamic Audio `29`.
- Dynamic Audio bands: Low `1`, Mid `2`, High `3`.
- Custom EQ profile: ID `4` with device-reported six-band schema.

Volume enhancer has no confirmed readback representation and remains hidden. ANC-button choices do not change the known `0x0108` mapping (`d1/b4/a1/f8`) and remain read-only. Proprietary spatial command `0x041E` is rejected and has no production control.

**Tradeoff:** The app intentionally offers fewer controls than the official UI when evidence is incomplete.

**Status:** Active.

## Excluded direct bass and speculative query paths

**Context:** Direct bass query `0x0124` times out, hearing command `0x0115` returns failure, and speculative spatial writes are rejected.

**Decision:** Use multi-band Dynamic Audio commands `0x012C`/`0x041B`; omit the failing paths from normal synchronization.

**Tradeoff:** Unsupported vendor features remain unavailable instead of delaying or invalidating the foreground session.

**Status:** Active.
