# Architecture Decisions (`context/DECISIONS.md`)

## 2026-08-02 — Clean-room Bluetooth Classic RFCOMM over vendor control UUID PSM 3

Context: The target device (realme Buds Wireless 5 ANC / RMA2412) uses a proprietary vendor protocol over Bluetooth Classic BR/EDR rather than standard Bluetooth LE GATT profiles. Official realme Link apps bundle heavy vendor SDKs, telemetry, accounts, and network requirements.  
Decision: Implement a clean-room protocol transport using RFCOMM via vendor UUID `0000079A-D102-11E1-9B23-00025B00A5A5` (PSM 3) with standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB` fallback, wrapping TL packets inside the OPPOv1 wire format (`0xAA` prefix + 7-bit varint length).  
Tradeoff: Proprietary vendor features that are not reverse-engineered or validated on physical hardware cannot be used. In exchange, the implementation requires zero accounts, zero cloud dependencies, zero analytics, and zero internet permission.  
Status: active

---

## 2026-08-03 — Foreground-persistent RFCOMM connection model over background service

Context: Bluetooth companion apps frequently employ persistent background services that drain battery, trigger aggressive OEM task kills, and demand persistent ongoing notifications in the Android notification shade.  
Decision: Connect to RFCOMM only while the app is in the foreground (lifecycle-bound `onStart`/`onStop` in `MainActivity`), disconnecting the socket immediately when the app is backgrounded. Launching is handled via a Quick Settings tile.  
Tradeoff: Headset telemetry is not monitored in the background, and noise/EQ toggles require bringing the app to the foreground. In return, phone and headset battery life is preserved, and the app requires zero background execution permissions.  
Status: active

---

## 2026-08-03 — Exclusion of direct bass engine command 0x0124 and unvalidated query paths

Context: Empirical testing on physical hardware revealed that direct bass query command `0x0124` consistently timed out across multiple reconnects. Similarly, `0x0115` returned failure status, and spatial command `0x041E` was rejected.  
Decision: Exclude command `0x0124`, `0x0115`, and `0x041E` entirely from initial capability discovery and UI pipelines. Multi-band bass is managed exclusively through commands `0x012C` (read) and `0x041B` (write).  
Tradeoff: Single-register direct bass query is unavailable; 3-band bass controls are used instead. Prevents RFCOMM socket timeouts and UI sync freezes during initial discovery.  
Status: active

---

## 2026-08-03 — Single-card collapse of gaming mode switches (IDs 6 and 8)

Context: The headset advertises ultra-low latency gaming mode under two distinct switch IDs: 6 and 8. Both IDs are accepted and toggle gaming mode, with ID 8 being the active baseline on initial read. Showing two separate gaming toggles in the UI creates user confusion.  
Decision: Collapse switch IDs 6 and 8 into a single logical "Game Mode (45ms)" control in `SwitchCatalog`, using ID 8 as the primary writable target and aliasing ID 6 to follow state changes defensively.  
Tradeoff: Hides raw protocol switch ID 6 from user-facing UI, requiring synthetic grouping logic in `SwitchCatalog`.  
Status: active

---

## 2026-08-03 — Quick Settings Tile as exclusive launcher entrypoint

Context: Headset companion settings are secondary utility controls. Cluttering the user's home screen or app drawer with a full launcher icon is unnecessary for quick toggles.  
Decision: Configure `MainActivity` with `android:exported="false"` without a launcher intent filter, and provide `UnlinkTileService` as the Quick Settings tile entrypoint. Tapping or long-pressing the tile opens the app.  
Tradeoff: Users must add the Quick Settings tile from Android's QS edit menu to access the app; it does not appear in the launcher drawer.  
Status: active

---

## 2026-08-04 — Asynchronous non-blocking coroutine dispatcher with optimistic UI updates

Context: Executing synchronous RFCOMM socket transactions on the UI looper during EQ preset or noise mode selection blocked the main thread and hung the application.  
Decision: Route all `HeadsetSession` reads and writes through `CoroutineScope(Dispatchers.IO + SupervisorJob())`. Apply state changes optimistically to `HeadsetSnapshot` immediately so the UI remains instant, with automated rollback to previous state if the hardware returns an error.  
Tradeoff: The UI state briefly displays the target value before packet confirmation is received over the wire; errors cause a visible rollback accompanied by a snackbar message. Guarantees zero UI thread blocking.  
Status: active

---

## 2026-08-04 — Domain model alignment to neckband hardware remote rather than TWS touch gestures

Context: Early iterations of the UI modeled touch gestures for separate left and right earbuds (TWS form factor). The realme Buds Wireless 5 ANC (RMA2412) is a neckband headset with physical hardware buttons (MFB, Volume +, Volume -, ANC key) and magnetic clasp detection.  
Decision: Replace earbud gesture cards with `HardwareButtonsCard` focused on the Multifunction Remote (MFB single, double, triple, long press), and update status copy to reference separating magnetic earbud clasps instead of opening a charging case.  
Tradeoff: Drops per-earbud touch mapping abstractions in favor of physical neckband button structures matching the hardware.  
Status: active

---

## 2026-08-04 — Delegate spatial audio configuration to Android OS spatializer

Context: Headset firmware returns command rejection on proprietary DSP spatial audio write `0x041E` despite reporting feature bit 27. Android 17 on the target Pixel 10 provides system-level spatial audio with dynamic head tracking.  
Decision: Do not send proprietary spatial audio writes to the headset. Render `SpatialAudioCard` showing Android OS spatializer availability and provide an intent link directly to Android system Bluetooth device settings.  
Tradeoff: The headset hardware does not perform onboard proprietary spatial processing; spatial audio processing is handled by Android OS.  
Status: active
