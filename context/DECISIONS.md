# Architecture Decisions

## Bluetooth Classic clean-room transport

**Context:** RMA2412 vendor controls use Bluetooth Classic RFCOMM and OPPOv1-framed TL packets. The official companion brings account, network, and telemetry dependencies.

**Decision:** Own a local RFCOMM implementation using vendor UUID `0000079A-D102-11E1-9B23-00025B00A5A5`, with standard SPP UUID fallback. Keep the app account-free and omit `INTERNET` permission.

**Tradeoff:** A feature is unavailable until its command and restoration behavior are validated on the target hardware.

**Status:** Active.

## Foreground-only control session

**Context:** Always-available headset events require a persistent process, control socket, foreground service, and notification.

**Decision:** Connect in `onStart`, disconnect in `onStop`, and launch through the Quick Settings tile. Exclude background telemetry and neckband-to-phone triggers; keep locator features foreground-only.

**Tradeoff:** Controls and state refresh require the app to be visible; no persistent app-owned radio or CPU footprint remains after backgrounding.

**Status:** Active.

## Exclusion of neckband-to-phone locator trigger

**Context:** The official companion app supports finding the phone by triggering an alert from the neckband. In Bluetooth Classic RFCOMM, listening for an asynchronous button press from the headset while the phone is locked or idle requires holding an active RFCOMM socket open indefinitely. On Android, this necessitates a persistent foreground service with an ongoing user notification, partial wake locks against Doze mode, and constant Bluetooth baseband activity. On the headset side, holding the RFCOMM link open prevents the headset SoC and radio from remaining in low-power deep sleep.

**Decision:** Deliberately reject and exclude neckband-to-phone trigger from implementation. Keep locator functionality unidirectional (phone-to-neckband only), operating strictly during active foreground UI sessions.

**Tradeoff:** The user cannot find their misplaced phone by pressing a button on the neckband. In exchange, zero background battery is consumed, no persistent foreground service notification is shown, and the application requires zero background or wake-lock permissions.

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
- ANC cycle modes: ANC and Off `3`, ANC and Transparency `9`, Transparency and Off `10`, All modes `11` via `0x010C`/`0x0404` (action `2`).
- MFB functions: None `0`, Play/Pause `1`, Voice Assistant `4`, Previous `5`, Next `6`, Game mode `17`.
- Switches: Game mode `6`, Auto answer `8`, Smart de-wind `26`, Spatial Audio `27`, Dynamic bass `29`.
- Dynamic bass bands: Low `1`, Mid `2`, High `3`.
- Preset EQ IDs: Nature Balance `0`, Bass Boost `1`, Clear Vocals `2`, Clear Bass `3`.
- Custom EQ profile: ID `4` with device-reported six-band schema.
- Volume enhancer: GET `0x0110`, SET `0x0407` with `[0x01]` (on) / `[0x00]` (off).

Volume enhancer, ANC-button mode-list cycling, and Spatial Audio were initially gated until reverse engineering established their exact opcode schemas, preventing speculative writes.

**Tradeoff:** The app intentionally offers fewer controls than the official UI when evidence is incomplete.

**Status:** Active.

## Excluded direct bass and speculative query paths

**Context:** Direct bass query `0x0124` times out, hearing command `0x0115` returns failure, and speculative spatial writes are rejected.

**Decision:** Use multi-band Dynamic bass commands `0x012C`/`0x041B` and switch `29`; omit the failing paths from normal synchronization.

**Tradeoff:** Unsupported vendor features remain unavailable instead of delaying or invalidating the foreground session.

**Status:** Active.

## Vendor wire protocol resolution via APK reverse engineering

**Context:** Volume Enhancer, ANC-button cycling, preset EQ profile IDs, and Spatial Audio had missing or contradictory wire encodings (e.g., speculative proprietary command `0x041E` for spatial audio was rejected by hardware).

**Decision:** Reverse engineer the official realme Link client bytecode and model assets (model ID `051412` for RMA2412) to derive authoritative wire schemas:

1. Volume Enhancer (`ITEM_SOUND_ENHANCE`): Query `0x0110` (response `0x8110`), write `0x0407` with payload `[0x01]` (on) / `[0x00]` (off). Command `0x0107` is Upgrade Capability and does not configure audio gain.
2. ANC Button Mode Cycling: Configured via action `2` of `0x010C` (GET `[0x02, 0x01]`) and `0x0404` (SET `[0x02, 0x01, cycleValue]`). Wire cycle values: ANC and Off (`3`), ANC and Transparency (`9`), Transparency and Off (`10`), All modes (`11`).
3. Preset EQ IDs: Resolved IDs 0..3 (Nature Balance `0`, Bass Boost `1`, Clear Vocals `2`, Clear Bass `3`) alongside Custom `4`. Allowed direct selection of all 4 presets.
4. Spatial Audio: Identified as standard switch feature ID `27` (`0x1B`) using standard switch query `0x010D` and write `0x0403`, rejecting speculative custom command `0x041E`.
5. Intentional feature exclusions:
   - *Find Phone from Headset:* Excluded to avoid keeping an active RFCOMM socket, foreground service, and partial wake locks permanently running while phone is locked.
   - *Earbud Fit Test (`ITEM_COMPACTNESS_DETECT`):* Excluded because neckband hardware has no optical in-ear proximity sensors.
   - *Golden Hearing / HearID (`ITEM_HEAR_ID`):* Excluded to adhere to the local-only boundary (vendor algorithm requires cloud hearing test profile calculation).

**Tradeoff:** Wire payloads follow exact vendor client definitions without requiring exploratory or unsafe write mutations against headset hardware.

**Status:** Active.
