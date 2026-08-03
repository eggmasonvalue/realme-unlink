# realme Buds Wireless 5 ANC — feasibility spike

Date: 2026-08-02

## Result

**Initial companion app feasibility: high.** The headset is currently bonded to the Pixel 10 and connected over Bluetooth Classic. realme Link is actively communicating with it. The device-specific implementation is present in the installed realme Link headset module, so we do not need to guess whether proprietary controls exist.

Firmware updates remain out of scope for the first prototype.

## Confirmed environment

- Device: `realme Buds Wireless 5 ANC`
- Phone: Pixel 10, Android 17, API 37
- realme Link: `5.5.514.6702` (`versionCode 525202`)
- Observed headset firmware: `1.1.0.64`
- Bluetooth transport observed by Android: BR/EDR ACL connection
- realme Link implementation selected at runtime: `TLHeadsetConnectionImp`

## Protocol evidence

The installed `split_realmeHeadset.apk` contains a complete third-party-facing implementation for this family of headsets:

- Bluetooth Classic RFCOMM transport
- Vendor-specific RFCOMM UUID: `0000079A-D102-11E1-9B23-00025B00A5A5`
- Standard SPP UUID fallback: `00001101-0000-1000-8000-00805F9B34FB`
- RFCOMM uses PSM 3 with a multiplexer/credit layer; the TL packet is carried inside an OPPOv1 application wrapper.
- OPPOv1 single-frame wire envelope:
  - start byte: `0xAA`
  - 7-bit length varint (little-endian groups)
  - link data: control byte `0x00`, reserved byte `0x00`, then the TL packet
  - multipart control values `1`, `2`, and `3` are first, middle, and final fragments
- TL packet format inside that envelope:
  - command: 16-bit little-endian
  - transfer ID: 1 byte
  - payload length: 16-bit little-endian
  - payload bytes
- The initial capability query uses command `0x0100`; responses set the high response bit (`0x8000`).

The runtime implementation builds packets locally and does not require a cloud service for the Bluetooth command path. The app does perform unrelated account/network operations, which our app will not need.

## Controls observed in realme Link

The realme Link sample log from the connected device reported:

- Battery: 80%
- Firmware: `1.1.0.64`
- Noise-control state and noise-control cycle information
- Button/key mappings
- Gaming mode
- Sound-effect/EQ state
- Custom EQ data with six bands: 50, 250, 1000, 4000, 8000, 16000 Hz
- Bass-engine values for low/mid/high adjustment
- High-volume enhancement state
- Dual-device connection state
- Wind-noise-control state
- Find-device command path

The same log reported that spatial-audio command `0x041e` is not supported by this device, so spatial audio should not be promised in our UI.

## Direct diagnostic validation

The standalone build was installed and exercised on the Pixel 10 after stopping realme Link. It connected through the vendor control UUID and produced valid, matching responses:

- `0x0100` → `0x8100`, payload `00 BF 1F DA 20 45 04 40 00`
- `0x0106` → `0x8106`, payload `00 01 01 46` (70%, device type 1)
- `0x010C` with selector payload `01 01` → `0x810C`, payload `00 01 01 08` (noise state)
- Safe write `0x0404`, payload `01 01 08` → `0x8404`, payload `00`
- A follow-up noise-state read returned `00 01 01 08`, confirming that the idempotent write did not change the setting.

Unsolicited `0x0501` frames were also observed and correctly ignored while matching responses by command and transfer ID.

## Implication for the app

We can implement a clean-room protocol layer around the observed wire format and expose only confirmed capabilities:

1. Discover the bonded headset by name.
2. Connect to the RFCOMM control service.
3. Query capabilities and device state.
4. Read battery/firmware information.
5. Add safe read-only diagnostics first. **Complete.**
6. Add one reversible write command (noise-control mode) only after packet responses are verified. **Complete: idempotent write/read-back verified.**
7. Build the polished Compose/Material 3 Expressive dashboard around that capability model.

No extracted APK, decompiled source, firmware, account data, or device identifiers should be committed to this repository. The protocol implementation should be written independently and tested against the user's device.

## Remaining gate

The direct protocol feasibility gate is complete: RFCOMM connection, wrapped capability query, battery/state reads, and one reversible noise-control write/read-back all succeeded. The next phase is the capability-driven dashboard; firmware remains out of scope.

## Sources

- [Gadgetbridge Realme support](https://gadgetbridge.org/gadgets/headphones/realme)
- [Gadgetbridge source](https://codeberg.org/Freeyourgadget/Gadgetbridge)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
