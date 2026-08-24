# Veyro Mobile ↔ Veyro Desktop

## Status

Veyro Mobile `0.1.10-alpha` implements the Android side of the transports introduced by Veyro Desktop Milestones 2 and 3:

- simultaneous BLE advertising and scanning;
- GATT service and client using the Veyro UUIDs;
- bilateral pairing with a derived PIN that is never transmitted;
- persistent identity protected by Android Keystore;
- public-key-based Trust Hub;
- signed challenge authentication for reconnection;
- router-independent Wi-Fi Direct link formation;
- fast-channel negotiation over BLE;
- socket binding to the Wi-Fi Direct interface address;
- mutual TLS 1.2/1.3, ALPN `veyro/1`, and pinning of the key confirmed during pairing;
- `VYRO` framing, version hello, keepalive, timeout, and authenticated resumption;
- transport of `VeyroMessage` data inside `TransportEnvelope`.

Automated tests and builds pass on both platforms. Physical testing confirmed bilateral BLE discovery and pairing, P2P recovery without toggling infrastructure Wi-Fi, and a successful Android 16 mutual-TLS session. The secure channel disconnected before the 45-second observation finished, so keepalive and authenticated resumption remain pending for the joint Desktop acceptance run.

## Preserved compatibility

The new transport does not replace Nearby Connections:

| Source | Destination | Transport |
| --- | --- | --- |
| Android | Android | Nearby Connections `P2P_STAR` |
| Android | Windows | BLE/GATT + Wi-Fi Direct + Veyro TLS |

Existing Android features and connections continue to use the same Nearby code. Discovered computers are added to the existing device list with a separate transport and reuse the same visual PIN-confirmation dialog.

## Requirements

### Android

- Android 10 or newer for the complete ALPN fast channel;
- Bluetooth LE peripheral advertising and GATT support;
- Wi-Fi Direct;
- Bluetooth and Wi-Fi enabled;
- Nearby Devices permissions granted;
- on Android 12/12L, precise location granted when required by the Wi-Fi Direct discovery API.

### Windows

- Veyro Desktop with Milestones 1–3;
- Bluetooth LE and Wi-Fi Direct support;
- Bluetooth and Wi-Fi enabled;
- application open during the first test.

The devices do not need internet access, a router, or membership in the same Wi-Fi network.

## User flow

1. Open Veyro on Android and enable the continuous ecosystem.
2. Open Veyro Desktop.
3. Select the other device from either nearby-device list.
4. Compare the six-digit PIN shown on both devices.
5. Confirm only when both values match.
6. Wait for authentication, Wi-Fi Direct formation, and secure-channel states.
7. Android should display `Secure channel active with <PC name>` in the selected UI language.
8. Desktop should report an active Wi-Fi Direct link and secure channel.

After the first pairing, both devices prove possession of their persistent keys with an ECDSA challenge. Revoking the PC from the Android Trust Hub blocks future reconnections, and the reverse also applies.

## Pairing security

- identity: persistent ECDSA P-256;
- ephemeral agreement: ECDH P-256;
- interoperable KDF: SHA-256 over the raw ECDH secret;
- verification: HMAC-SHA-256 over the canonical transcript;
- signature: ECDSA/SHA-256 in fixed 64-byte P1363 format;
- transcript domains: `Veyro.PairingHello.v1`, `Veyro.PairingVerification.v1`, and `Veyro.PairingConfirmation.v1`;
- hello time window: two minutes;
- mandatory confirmation on both devices.

The Android Keystore identity is versioned as `v2`. It authorizes SHA-256 for Veyro protocol signatures and the raw ECDSA operation used internally by Conscrypt after the TLS transcript has already been hashed. Upgrading from `0.1.9-alpha` rotates this key and therefore requires one new PIN confirmation with previously trusted Desktop installations.

Debug builds intentionally auto-confirm pairing on both transports to speed up controlled hardware diagnosis. The interface identifies this behavior with a `DEBUG` message. Non-debug builds always retain bilateral PIN confirmation.

On the fast channel, the presented certificate must contain the trusted device ID in its `CN` and exactly match the public SPKI stored in the Trust Hub. A public certificate chain does not replace bilateral pinning.

## Physical acceptance checklist

Keep Bluetooth and Wi-Fi enabled. Begin while Android remains connected to its normal Wi-Fi network to verify infrastructure/P2P coexistence; then repeat without router association while leaving the Wi-Fi radio enabled.

1. Confirm that Android and Desktop discover each other over BLE.
2. Start pairing from Android and confirm matching PINs.
3. Revoke trust and repeat with pairing initiated from Desktop.
4. Confirm that a mismatched PIN or a rejection never creates trust.
5. Confirm Wi-Fi Direct group formation without a LAN.
6. Confirm mutual TLS, ALPN `veyro/1`, and the protocol hello.
7. Observe keepalive behavior for at least 60 seconds.
8. Interrupt only the Wi-Fi Direct group and verify reconstruction and resumption within five minutes without disconnecting the normal Wi-Fi network.
9. Disable Bluetooth after the fast channel is active and record the behavior.
10. Confirm that an Android ↔ Android session still works through Nearby after the PC tests.

During testing, collect visible status information from both interfaces and sanitized Desktop logs. Never record PINs, keys, clipboard content, or message payloads.

## Current limitations

- BLE discovery, bilateral pairing, separate-interface Wi-Fi Direct, and Android 16 mutual TLS have been physically accepted on the current Android/Windows pair. The session later entered its five-minute resumption window, so keepalive stability and authenticated resumption are not yet accepted.
- The fast channel requires Android 10 or newer. Older Android devices can continue to use Nearby with other Android devices.
- Desktop Milestone 3 establishes and maintains the transport but does not yet expose every Android feature in its UI.
- Large files still use the dedicated Nearby payload between Android devices. Desktop fast-channel file streaming must be specified before it is enabled.
- When multiple unidentified Wi-Fi Direct groups are nearby, Android avoids silently selecting one. Keep only the intended PC available during the first test.

## Quick diagnostics

| Symptom | Check |
| --- | --- |
| PC is not visible | Bluetooth enabled, Nearby Devices permission, BLE advertising support, and Desktop application open |
| PIN is not shown | GATT availability, Veyro characteristic discovery, and notifications enabled |
| PIN differs | clocks within the two-minute window and both builds using the current shared contract |
| Wi-Fi Direct does not form | Wi-Fi enabled, applicable Nearby Wi-Fi/location permission, and Windows adapter driver |
| Normal Wi-Fi disconnects | Treat as a driver/platform concurrency issue; Veyro itself resets only its P2P group and never toggles infrastructure Wi-Fi |
| TLS fails | trust not revoked, SPKI equal to the paired key, correct date/time, and ALPN `veyro/1` |
| Channel drops after 15 seconds | blocked keepalive, lost P2P interface, or socket bound to the wrong address |
