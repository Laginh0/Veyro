# Veyro

Veyro is a peer-to-peer ecosystem that connects nearby devices directly, without requiring a central cloud service to transport data. Every device can discover, receive, and send information through the same interface, with no fixed sender or receiver role.

> Current status: **Alpha 0.2.0**. This version gives every feature a dedicated, responsive full-screen workspace and hardens identity, authorization, replay protection, transport failover, and file integrity across Nearby and Veyro Desktop routes.

> Debug builds expose disconnected feature previews for interface validation. Pairing still requires bilateral PIN confirmation, and Release builds do not include the disconnected preview mode.

This folder is the standalone Android project. Its versioned Protobuf contracts are included in `src/main/proto/` so the published Mobile repository builds independently.

## Key features

| Feature | Description |
| --- | --- |
| Continuous connection | Keeps the device visible while finding other Veyro devices at the same time. |
| Veyro Desktop interoperability | Discovers Windows peers over BLE, performs bilateral cryptographic pairing, creates a router-independent Wi-Fi Direct link, and opens a mutually authenticated TLS channel. |
| Automatic reconnection | Attempts to restore the link when a known device returns within range. |
| Trust Hub | Stores known devices and provides individual trust rules for each one. |
| File transfer | Sends and receives files directly, with progress information and local approval controls. |
| Battery sync | Displays the connected device's charge level and power source. |
| Connectivity report | Shares the active transport, validated internet access, metered-network state, and signal strength when Android exposes it. |
| P2P ping | Measures round-trip latency between connected Veyro devices at intervals adapted to the selected power mode. |
| Multi-device sessions | Lets one hub keep several satellites connected while the interface selects one active control target. |
| Contact sync | Sends only a contact explicitly selected in Android and requires local confirmation before import; photos are not included. |
| Presentation mode | Provides previous/next slide controls, a remote blackout, and a synchronized timer. |
| Drawing tablet | Transmits stylus identity, normalized position, pressure, tilt, and primary-button state. |
| Shared remote folder | Exposes only a directory explicitly selected through Android's Storage Access Framework and keeps all other storage inaccessible. |
| Notification sync | Shares authorized notifications and supports remote dismissal. |
| Media control | Synchronizes artwork, title, artist, playback position, duration and volume; provides seeking, remote media notifications, output-route visibility, and per-stream audio controls. |
| Find my device | Starts and stops an audible alarm on the connected device. |
| Calls and SMS | Synchronizes call state and requires local confirmation before sending a remote SMS. |
| Link sharing | Sends web addresses for user-approved opening on another device. |
| Clipboard sync | Synchronizes plain text when the user returns to Veyro, taps the manual action, or uses the optional Quick Settings tile; rich and sensitive content is never transmitted. |
| Safe commands | Provides a restricted set of remote actions, including volume and flashlight control. |
| Remote input | Performs gestures and text input when the user explicitly enables the accessibility service. |
| Feature control center | Provides persistent switches that independently enable or disable every ecosystem module, including connectivity reports and P2P ping. |
| Adaptive navigation | Uses a compact navigation drawer on phones and a navigation rail on larger screens. |
| Portuguese and English UI | Switches the interface language from Settings and remembers the selection. |

## Continuous connection architecture

Veyro uses Google Nearby Connections with the `P2P_STAR` strategy. When the ecosystem is enabled, the device becomes visible to other Veyro installations and starts finding nearby devices simultaneously.

Each installation receives a persistent identity. Dynamic hub selection considers:

- battery level;
- whether the device is connected to power;
- available processing capacity;
- a persistent identifier for deterministic tie-breaking.

The lower-capacity device initiates the connection toward the higher-capacity device. When scores are equal, persistent identifiers determine a single direction. A deterministic delay between 100 and 300 ms prevents competing simultaneous requests.

After the first connection defines the topology, a device keeps one role for that session: an advertiser acts as the hub and may accept multiple satellites, while a discoverer acts as a satellite and connects to one hub. Controls and remote state are scoped to the active device selected in the interface; periodic local reports are delivered independently to every connected endpoint.

After the first PIN confirmation, Trust Hub pins the peer's P-256 identity key. Every later Nearby connection exchanges a signed identity claim bound to that connection's authentication digits before application data is accepted. If connectivity is lost, the foreground service remains available and attempts to reconnect. When enabled, the ecosystem can also resume after an Android restart or an app update.

## Power modes

Users can choose one of three behaviors:

- **Continuous:** prioritizes availability and keeps the service ready at all times.
- **Balanced:** maintains continuous connectivity while using wake locks only when required.
- **Battery saver:** while the screen is off, alternates short device-detection windows with sleep intervals.

During a file transfer, Veyro temporarily adds the Android data synchronization foreground-service type. Outside transfers, it uses only the connected-device service type.

## Security and privacy

- The first connection requires users to compare the same PIN on both devices.
- Nearby endpoint IDs and display names never define identity. The persistent P-256 key is pinned, and a matching signed claim is required on every connection.
- Nearby and Desktop relay messages use the same signed, end-to-end encrypted logical envelope with a message ID, sender epoch, sequence, expiry, destination, and bounded replay cache.
- A Desktop may advertise a route to another Android only when that Android key is already pinned locally. This preserves routing authority without granting the Desktop identity authority.
- Files from unknown devices wait for local approval.
- Trust Hub permissions are configured independently for every known device.
- Every remote SMS request requires confirmation on the device that will send it.
- Remote input accepts only commands defined by the Veyro protocol.
- Contacts are never imported without a local confirmation and are transferred without photos.
- Remote file requests are validated against the persisted SAF tree selected by the folder owner.
- Clipboard sync is opt-in, text-only, limited to 20 KB, and deduplicated to prevent relay loops. Clips marked sensitive are rejected before transmission, while received clips are tagged as remote-device content. Android may require Veyro to be in the foreground before clipboard text can be read; the optional Quick Settings tile provides a one-tap focused action. Received text is marked as sensitive to hide its preview, and Android displays a local copy confirmation.
- Initial setup requests only the nearby-device access required for direct connections. Calls, SMS, contacts, camera, notification access, Do Not Disturb access, and Accessibility remain optional and are requested in context when the user enables or invokes the related feature.
- The accessibility service does not transmit screen contents.
- Android-to-Android communication continues to travel directly through Nearby Connections.
- Android-to-Windows communication uses BLE/GATT for discovery and pairing, then Wi-Fi Direct and mutual TLS for the fast channel. It does not require a router or local Wi-Fi network. See [`docs/DESKTOP_INTEROPERABILITY.md`](docs/DESKTOP_INTEROPERABILITY.md).
- Wi-Fi Direct recovery resets only the P2P group. Veyro never disables, disconnects, or denies the device's normal Wi-Fi connection.
- Bouncy Castle is pinned to 1.84, the first line used by Veyro that includes the fix for CVE-2026-5588.

The security improvements, delivery guarantees, and remaining risks for this release are documented in [`docs/SECURITY_IMPROVEMENTS_0.2.0_ALPHA.md`](docs/SECURITY_IMPROVEMENTS_0.2.0_ALPHA.md).

## Permissions

Permissions are requested only for the features that require them:

| Permission or special access | Purpose |
| --- | --- |
| Bluetooth and nearby devices | Find and connect to Veyro devices. |
| Nearby Wi-Fi | Discover and form the direct radio link used by Nearby or Veyro Desktop. |
| System notifications | Keep the continuous foreground service visible to the user. |
| Notification access | Synchronize authorized notifications and media state. |
| Notification policy access | Allow the find-device alarm to work correctly. |
| Phone, contacts, and SMS | Synchronize calls and process user-confirmed SMS requests. |
| Write contacts | Import a received contact only after the user taps the local confirmation button. |
| Storage Access Framework folder grant | Share read access only to the directory selected by the user; no broad storage permission is requested. |
| Camera | Control the flashlight when the user requests that command. |
| Accessibility | Perform user-authorized remote input. |

Denying an optional permission does not prevent basic file transfers.

## Requirements

- Android 6.0 or newer (`minSdk 23`), required for the persistent Android Keystore EC identity.
- Google Play Services with Nearby Connections support.
- Bluetooth and Wi-Fi available on the device.
- Android Studio or JDK 17 to build the project.

## Build

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

The APK is generated at:

```text
build/outputs/apk/debug/VeyroMobile-debug.apk
```

## Install with ADB

```powershell
adb install -r -t build/outputs/apk/debug/VeyroMobile-debug.apk
```

If a currently installed build uses a different signing key, Android requires uninstalling it first. Uninstalling removes local settings and devices stored in Trust Hub.

## Tests

The project includes:

- unit tests for device identity, hub selection, and the communication protocol;
- Android instrumented tests for services and persistent preferences;
- Android Lint static analysis;
- manual validation on Android 16.

## Project structure

```text
mobile/
├── src/main/java/com/veyro/p2p/  Android application code
├── src/main/res/                 Android resources
├── src/test/                     Unit tests
├── src/androidTest/              Instrumented tests
└── docs/                         Android hardware test plans
```

## Acknowledgements

Veyro was inspired in part by [KDE Connect](https://kdeconnect.kde.org/) and its approach to private continuity between devices, including ideas around file sharing, notifications, media control, remote input, and device discovery.

Veyro is an independent project with its own interface, architecture, and roadmap. It is not affiliated with, sponsored by, or endorsed by KDE e.V. or KDE Connect.

## Current release

The latest published test build is [Veyro Alpha 0.2.0](https://github.com/Laginh0/Veyro/releases/tag/v0.2.0-alpha). It keeps bilateral PIN confirmation enabled, introduces a consistent full-screen layout for every feature, and unifies signed and encrypted application envelopes across Nearby and Desktop relay routes.

Alpha APKs use a development signing key. Confirm that an existing installation uses the same key before attempting an update.

## Alpha limitations

- The interface and protocol may change before the stable release.
- Aggressive battery optimizations from some Android manufacturers may interrupt background services.
- Full interoperability testing across different manufacturers and Android versions is still in progress.
- At-most-once protection is currently process-scoped; durable transactional replay protection after a crash remains planned.
- Per-peer Nearby fallback after a partial Desktop-star failure and relayed Android-to-Android file transfer are not complete.
- This build is not intended for production distribution.
