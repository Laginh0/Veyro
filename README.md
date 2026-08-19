# Veyro

Veyro is a peer-to-peer Android ecosystem that connects nearby devices directly, without requiring a central cloud service to transport data. Every device can discover, receive, and send information through the same interface, with no fixed sender or receiver role.

> Current status: **Alpha 0.1.1**. This version is under active development and intended for testing.

## Key features

| Feature | Description |
| --- | --- |
| Continuous connection | Keeps the device visible while finding other Veyro devices at the same time. |
| Automatic reconnection | Attempts to restore the link when a known device returns within range. |
| Trust Hub | Stores known devices and provides individual trust rules for each one. |
| File transfer | Sends and receives files directly, with progress information and local approval controls. |
| Battery sync | Displays the connected device's charge level and power source. |
| Notification sync | Shares authorized notifications and supports remote dismissal. |
| Media control | Synchronizes playback state and sends media commands. |
| Find my device | Starts and stops an audible alarm on the connected device. |
| Calls and SMS | Synchronizes call state and requires local confirmation before sending a remote SMS. |
| Link sharing | Sends web addresses for user-approved opening on another device. |
| Safe commands | Provides a restricted set of remote actions, including volume and flashlight control. |
| Remote input | Performs gestures and text input when the user explicitly enables the accessibility service. |
| Feature control center | Provides persistent switches that independently enable or disable files, battery, notifications, media, calls, links, commands, device finding, and remote input. |
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

After the first PIN confirmation, Trust Hub recognizes the device. If connectivity is lost, the foreground service remains available and attempts to reconnect. When enabled, the ecosystem can also resume after an Android restart or an app update.

## Power modes

Users can choose one of three behaviors:

- **Continuous:** prioritizes availability and keeps the service ready at all times.
- **Balanced:** maintains continuous connectivity while using wake locks only when required.
- **Battery saver:** while the screen is off, alternates short device-detection windows with sleep intervals.

During a file transfer, Veyro temporarily adds the Android data synchronization foreground-service type. Outside transfers, it uses only the connected-device service type.

## Security and privacy

- The first connection requires users to compare the same PIN on both devices.
- Files from unknown devices wait for local approval.
- Trust Hub permissions are configured independently for every known device.
- Every remote SMS request requires confirmation on the device that will send it.
- Remote input accepts only commands defined by the Veyro protocol.
- The accessibility service does not transmit screen contents.
- Communication travels directly between devices through Nearby Connections.

## Permissions

Permissions are requested only for the features that require them:

| Permission or special access | Purpose |
| --- | --- |
| Bluetooth and nearby devices | Find and connect to Veyro devices. |
| Nearby Wi-Fi | Negotiate the available P2P transport. |
| System notifications | Keep the continuous foreground service visible to the user. |
| Notification access | Synchronize authorized notifications and media state. |
| Notification policy access | Allow the find-device alarm to work correctly. |
| Phone, contacts, and SMS | Synchronize calls and process user-confirmed SMS requests. |
| Camera | Control the flashlight when the user requests that command. |
| Accessibility | Perform user-authorized remote input. |

Denying an optional permission does not prevent basic file transfers.

## Requirements

- Android 5.0 or newer (`minSdk 21`).
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
app/build/outputs/apk/debug/app-debug.apk
```

## Install with ADB

```powershell
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
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
app/src/main/java/com/veyro/p2p/
├── features/       Battery, media, notifications, telephony, and remote control
├── nearby/         Device discovery, connection, hub selection, and P2P transfer
├── permissions/    Android runtime permissions
├── protocol/       Messages exchanged between devices
├── service/        Continuous foreground service
├── settings/       Trust Hub, identity, language, and power modes
├── storage/        Storage for received files
└── ui/             Theme, interface components, and translations
```

## Acknowledgements

Veyro was inspired in part by [KDE Connect](https://kdeconnect.kde.org/) and its approach to private continuity between devices, including ideas around file sharing, notifications, media control, remote input, and device discovery.

Veyro is an independent project with its own interface, architecture, and roadmap. It is not affiliated with, sponsored by, or endorsed by KDE e.V. or KDE Connect.

## Current release

The current test build is [Veyro Alpha 0.1.1](https://github.com/Laginh0/Veyro/releases/tag/v0.1.1-alpha).

Alpha APKs use a development signing key. Confirm that an existing installation uses the same key before attempting an update.

## Alpha limitations

- The interface and protocol may change before the stable release.
- Aggressive battery optimizations from some Android manufacturers may interrupt background services.
- Full interoperability testing across different manufacturers and Android versions is still in progress.
- This build is not intended for production distribution.
