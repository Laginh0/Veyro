# Veyro Two-Device Test Plan

Planned execution date: August 21, 2026

## Objective

Validate that two Android devices can discover, pair, trust, exchange status, recover from interruptions, and use Veyro features without crashes or inconsistent state. The session will also establish baseline latency and reconnection measurements for later comparisons.

## Test devices

- **Device A:** primary development phone, connected to ADB for logs and screenshots.
- **Device B:** secondary phone, running the same Veyro build.
- Record the Android version, Veyro commit, battery level, selected power mode, and active network for both devices before starting.

Do not store wireless-debugging pairing codes, personal phone numbers, notification contents, or other private data in the repository.

## Preparation

1. Build one APK from a clean Git commit and install that exact APK on both devices.
2. Confirm Bluetooth, Wi-Fi, Nearby Devices, and notification permissions.
3. Keep both devices unlocked and less than two metres apart for the first run.
4. Set the app language to English on one device and Portuguese on the other.
5. Clear Logcat, start a timestamped test note, and record the Git commit hash.
6. Confirm that no old Veyro connection is active. Preserve Trust Hub records unless a test explicitly requires a first-time pairing.

## Evidence to collect

For every failed or inconsistent test, capture:

- test case ID and exact time;
- actions performed on each device;
- screenshot or short screen recording from both devices;
- relevant ADB Logcat excerpt;
- whether retrying changed the result;
- battery level, power mode, and network state.

No sensitive notification, contact, SMS, or clipboard content should appear in saved evidence. Use synthetic test data.

## Test sequence

### 1. Discovery, pairing, and Trust Hub

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| CON-01 | Bidirectional discovery | Enable the continuous ecosystem on A, then B. Repeat with the activation order reversed. | Both activation orders reveal the other device once, without duplicate cards or competing connection requests. |
| CON-02 | First pairing | Remove the existing trust record if needed, connect, and compare the PIN on both devices. | Both devices display the same PIN and become connected only after both users confirm. |
| CON-03 | Pairing rejection | Start a fresh pairing and reject it on one device. | Neither device is trusted or left in a false connected state. |
| CON-04 | Trust persistence | Pair successfully, close both apps, reopen them, and reconnect. | Trust is retained and the known device reconnects according to its saved rules. |
| CON-05 | Trust removal | Remove B from A's Trust Hub and reconnect. | A requires a new trust confirmation and does not silently restore the old trust. |

### 2. Omnidirectional continuity and recovery

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| REC-01 | App backgrounding | Connect the devices, place both apps in the background for five minutes, then reopen them. | The connection remains usable or is restored automatically without duplicate sessions. |
| REC-02 | Screen off | Lock A for five minutes, then repeat with B and with both devices locked. | Behaviour matches the selected power mode and the UI recovers to the correct state. |
| REC-03 | Range interruption | Move B out of range or disable its radios for two minutes, then restore them. | Both devices leave stale connected state and reconnect when transport returns. |
| REC-04 | Wi-Fi transition | While connected, change one device from one Wi-Fi network to another and then return. | Veyro reports the transport change and recovers without manual app restart. |
| REC-05 | Bluetooth transition | Disable and re-enable Bluetooth on one device. | State and user messaging remain correct; discovery resumes after Bluetooth returns. |
| REC-06 | Process restart | Force-stop Veyro on B, reopen it, and wait for recovery. | A detects the disconnect and both devices establish a single valid session afterward. |
| REC-07 | Device restart | Reboot one device with automatic resume enabled. | The service resumes according to Android restrictions and reconnects without corrupting trust. |

### 3. Connectivity report

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| NET-01 | Wi-Fi report | Connect both devices on Wi-Fi and inspect the remote status card. | Transport shows Wi-Fi; internet, metered state, and available signal data match Android's current state. |
| NET-02 | Cellular report | Move one device to cellular data while maintaining a usable Nearby connection. | The remote report changes to cellular and marks the network as metered when Android does. |
| NET-03 | No validated internet | Keep the local transport active but remove internet access. | The report distinguishes transport availability from validated internet access. |
| NET-04 | Toggle isolation | Disable Connectivity report on A only, then change B's network. | A stops displaying/updating the report while unrelated features continue working. |
| NET-05 | Re-enable | Enable the report again while connected. | A receives a fresh status without reconnecting the whole session. |

### 4. P2P ping

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| PNG-01 | Baseline latency | Leave both devices idle and connected for at least five ping samples. | A valid round-trip time appears and continues to update without accumulating duplicate events. |
| PNG-02 | Bidirectional measurement | Compare the ping card on A and B over the same period. | Both directions produce plausible independent measurements. |
| PNG-03 | Power-mode cadence | Repeat in Continuous, Balanced, and Battery saver modes. | Approximate intervals are 10, 20, and 60 seconds respectively. |
| PNG-04 | Interrupted request | Disable the transport immediately after a ping cycle begins, then restore it. | Timed-out requests do not produce negative, extreme, or stale latency after reconnection. |
| PNG-05 | Toggle isolation | Disable Ping on one device only. | Ping updates stop for that device while connectivity reports and other modules remain usable. |
| PNG-06 | Long session | Leave ping enabled for at least 30 minutes. | Memory use remains stable, the app does not crash, and RTT updates continue. |

For PNG-01 and PNG-03, record minimum, median, maximum, sample count, and any missing samples. These values are diagnostic baselines, not hard pass/fail limits for the alpha build.

### 5. Power modes

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| PWR-01 | Continuous | Run connected with screens on and off for 15 minutes. | Availability is prioritised and periodic ping uses the continuous cadence. |
| PWR-02 | Balanced | Repeat the same scenario in Balanced mode. | Connection remains stable and ping cadence changes without reconnecting manually. |
| PWR-03 | Battery saver | Lock both devices long enough to cover sleep and discovery windows. | Reduced activity is observable, followed by successful discovery/recovery in an allowed window. |
| PWR-04 | Mode change while connected | Cycle through all modes during one session. | No crash, duplicate session, or stale UI occurs; the new policy takes effect. |

### 6. Feature regression

Run one successful exchange in each direction where the feature supports it:

- file transfer, approval, progress, and received-file integrity;
- battery status;
- notification sync and remote dismissal using a synthetic notification;
- media state and remote media commands;
- link sharing with local approval;
- find-device start and stop;
- safe remote commands;
- call-state sync and a synthetic SMS confirmation flow where practical;
- remote input only after explicitly enabling Accessibility;
- clipboard sync, if its implementation is ready before the session.

For each module, disable its switch on the receiving device and verify that only that module stops. Re-enable it and verify recovery without rebuilding or clearing app data.

### 7. Clipboard synchronization (Android 16)

Use synthetic text only. Before each case, clear the clipboard history on both devices and keep any password manager or banking app closed. Never save clipboard contents in Logcat or screenshots.

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| CLP-01 | Manual A to B | Connect both devices, copy a short synthetic sentence on A, return to Veyro, and trigger clipboard synchronization. Paste on B. | B receives exactly the same plain text once and Android shows local copy feedback. |
| CLP-02 | Manual B to A | Repeat CLP-01 in the opposite direction. | A receives exactly the same text once; clipboard synchronization is bidirectional. |
| CLP-03 | Foreground return | Put Veyro in the background on A, copy text, and return to Veyro while connected. | Veyro reads and sends the clipboard only after it regains foreground access; no permission workaround is required. |
| CLP-04 | Background restriction | Keep Veyro in the background on A after copying text and wait two minutes. | Android 16 does not allow Veyro to silently read the new clipboard while unfocused; the app remains stable and explains the required user action. |
| CLP-05 | Quick Settings tile | Add the Veyro Clipboard tile from the feature panel, background Veyro, copy text, and tap the tile. | A brief focused action runs, the tile collapses, and the text reaches the connected device without leaving a Veyro screen in Recents. |
| CLP-06 | Tile while unavailable | Tap the tile first with Clipboard disabled and then with the ecosystem disabled. | No text is read or sent; Veyro displays the correct local instruction for each disabled state. |
| CLP-07 | Sensitive marker | Create a synthetic clip marked `EXTRA_IS_SENSITIVE` on A with a small test helper, then request synchronization. | Veyro refuses to transmit it and reports that sensitive content was not shared. |
| CLP-08 | Remote-device marker | Create a synthetic clip marked `EXTRA_IS_REMOTE_DEVICE`, then request synchronization. | Veyro refuses to retransmit it and identifies it as content that already came from another device. |
| CLP-09 | Relay-loop prevention | Send text from A to B, leave automatic synchronization enabled, and observe both devices for two minutes. | The received clip is not echoed back; counters, UI messages, and logs show a single transfer. |
| CLP-10 | Duplicate content | Trigger synchronization three times without changing the clipboard. | The same fingerprint is not transmitted repeatedly and neither device enters a retry loop. |
| CLP-11 | Size boundary | Send text just below 20 KB, exactly 20 KB, and just above 20 KB. | Text up to the limit is accepted; content above the limit is rejected locally with no partial payload. |
| CLP-12 | Rich and empty content | Try an image-only clip, formatted content without plain text, and an empty clipboard. | Nothing is transmitted and other connected features remain usable. |
| CLP-13 | Feature isolation | Disable Clipboard synchronization on B, try both directions, then re-enable it while still connected. | Clipboard traffic is blocked according to the disabled endpoint policy and resumes after re-enabling without reconnecting. |
| CLP-14 | Reconnect and process recovery | Disconnect during a clipboard request, reconnect, then repeat after force-stopping one device. | No stale text is applied after the disconnect; a new explicit synchronization works after recovery. |
| CLP-15 | Language and privacy feedback | Run one successful copy and one rejected sensitive clip with A in Portuguese and B in English, then swap languages. | Messages are localized, do not expose clipboard text, and remain understandable on both devices. |

For CLP-01, CLP-02, CLP-05, and CLP-09, record the request time, receipt time, direction, payload byte count, and whether Android displayed copy feedback. Record only hashes or fixed test-case labels, never the actual clipboard text.

### 8. Language and interface regression

- Confirm all new connectivity and ping labels in Portuguese and English.
- Check the phone layout in portrait mode on both screen sizes.
- Verify that drawers, bottom navigation, cards, switches, dialogs, and system insets do not overlap.
- Rotate a device during a connection and confirm that state is preserved.
- Confirm that status text is understandable when no second device is connected.

### 9. Optional permissions and feature activation

Run the clean-install cases on a disposable test profile or after exporting any state that must be preserved. Do not clear the primary device's app data solely for these cases.

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| PER-01 | Minimal first-run consent | Install 0.1.9 on a clean profile and start setup. | Veyro requests only the nearby-device permissions required for discovery and connection; notification, phone, SMS, contacts, camera, Modes, and Accessibility are not requested. |
| PER-02 | Notification synchronization activation | Enable notification synchronization without Notification Listener access. | A contextual explanation appears first; the feature remains off if canceled or denied and turns on only after Android confirms access. |
| PER-03 | Media control shared access | Enable media control with Notification Listener access denied, then grant it. | The same transparent explanation is shown; granting access enables the module without enabling unrelated modules. |
| PER-04 | Calls and SMS activation | Enable Calls and SMS on Android 13 or later. Deny one permission, then retry and grant the complete group. | Phone state, contacts, SMS, and app notifications are requested together after explanation. Partial denial leaves the module off; complete consent enables it. Caller-ID role remains separately identified as optional. |
| PER-05 | Safe actions activation | Revoke Camera, then enable Safe remote actions. | Veyro explains that Camera is used only for the flashlight and does not capture images. Denial leaves the module off. |
| PER-06 | Find-device activation | Remove Modes/Do Not Disturb access and enable Find device. | Veyro explains why audio-policy access is needed and opens the correct Android settings screen; denial leaves the module off. |
| PER-07 | Remote input activation | Disable the Veyro Accessibility service and enable Remote mouse and keyboard. | Veyro explains gestures/text injection and states that screen contents are not transmitted. The module stays off until the service is enabled. |
| PER-08 | Enabled count accuracy | Leave Accessibility denied while all other modules are available. | The control center shows 15 of 16 enabled, Remote mouse and keyboard is off, and no access card claims all features are usable. |
| PER-09 | Permission revocation | Enable each privileged module, revoke its Android access outside Veyro, and return to the app. | The affected module is automatically disabled and its enabled count decreases; all unrelated modules retain their state. |
| PER-10 | Permission-free toggles | Enable and disable Battery, Connectivity report, Ping, Presentation, Drawing tablet, Remote folder, and Clipboard where applicable. | No unrelated runtime permission dialog appears. SAF and clipboard foreground actions remain scoped to the moment they are used. |
| PER-11 | Contact import action scope | Enable Contact sync, share a selected contact, and approve an incoming import without `WRITE_CONTACTS`. | Selection remains explicit; write permission is requested only when the local user approves the import, not during initial setup. |
| PER-12 | Bilingual consent copy | Repeat one runtime permission and one special-access activation in Portuguese and English. | Titles, explanations, grant action, and keep-disabled action are fully localized and fit both screens. |

### 10. Veyro Desktop interoperability

Run these cases with the current Windows Debug build and Android `0.1.10-alpha`. Keep infrastructure Wi-Fi connected first to verify coexistence, then repeat without router association while leaving both radios enabled. Debug builds auto-confirm pairing; repeat all security-consent cases later with non-debug builds.

| ID | Test | Procedure | Expected result |
| --- | --- | --- | --- |
| DSK-01 | Bidirectional BLE discovery | Start both apps and observe their nearby lists. | Android sees a `Veyro Desktop` entry and Windows sees the Android ephemeral advertisement; no persistent ID appears in advertising data. |
| DSK-02 | Pairing initiated by Android | Select the PC on Android and compare the six digits. Confirm on both. | PINs match, both confirmations are required, and both Trust Hubs store the peer key. |
| DSK-03 | Pairing initiated by Windows | Revoke the trust, select Android on Windows, and repeat. | The inverse GATT client/server direction succeeds with the same security properties. |
| DSK-04 | Refusal and tampering boundary | Refuse once on Android and once on Windows. | No trusted peer is created and Wi-Fi Direct does not start. |
| DSK-05 | Wi-Fi Direct without LAN | After trust, wait for the P2P group while infrastructure Wi-Fi remains disconnected. | Windows is group owner, Android is group client, and both obtain only link-local P2P endpoint addresses. |
| DSK-06 | Mutual TLS and ALPN | Observe the status after the P2P link forms. | TLS 1.2/1.3 succeeds only with the keys stored during pairing and negotiates `veyro/1`. |
| DSK-07 | Framing and keepalive | Leave the channel idle for at least 60 seconds. | `VYRO` frames and keepalives continue; neither side times out after 15 seconds. |
| DSK-08 | Resume window | Disable Wi-Fi briefly, restore it within five minutes, and wait. | The direct link is rebuilt and the signed resume token restores the logical session without duplicate delivery. |
| DSK-09 | Revocation | Revoke Android on Windows, then attempt reconnection; repeat in the other direction. | Challenge proof or TLS pinning rejects the revoked peer and no fast channel becomes active. |
| DSK-10 | Android Nearby regression | Close Desktop and connect the two Android devices normally. | Discovery, PIN, connection, ping, clipboard and one small file continue through Nearby Connections. |

### 2026-08-21 preliminary result

- Pass: DSK-01 BLE discovery in both application interfaces.
- Pass: Debug pairing completed without PIN UI on Android or Windows, as explicitly configured for this diagnostic build.
- Pass: Wi-Fi Direct coexisted with normal Wi-Fi; Android received `192.168.137.91` on `p2p-p2p0-0` while `wlan0` remained enabled.
- Pass: Android 16 mutual TLS became active and Desktop displayed one secure session; the former Keystore `INCOMPATIBLE_DIGEST` error did not recur.
- Fail/pending: DSK-07. The channel entered the five-minute resumption window before the 45-second observation completed.
- Pending: DSK-08 authenticated resumption and the remaining feature tests. Resume after Desktop features are complete.

Do not mark DSK-02 through DSK-08 as passed from automated tests alone. They require the physical radios and both visible application states.

### 2026-08-21 Android + Windows execution report

Test environment: POCO 2311DRK48I, Android 16/API 36, Veyro Mobile `0.1.10-alpha` Debug, current Veyro Desktop Debug, Android connected by USB ADB. No repository upload was performed. A second Android device was unavailable.

#### Automated coverage

- Pass: Mobile build, APK assembly, and lint.
- Pass: 44/44 Mobile JVM unit tests.
- Pass: 14/15 Mobile instrumented tests on the physical Android during the first run. The battery monitor case timed out waiting for the first sticky battery update.
- Fixed and retested: the battery monitor now reads the sticky battery intent before registering for later updates. Its isolated physical-device test passed in 10.058 seconds, bringing the exercised instrumented cases to 15/15 across the two runs.
- Pass: Desktop Debug build with zero warnings and zero errors.
- Pass: 32/33 Desktop tests. The remaining `SecureFastChannelTests.Resume_token_is_scoped_expires_and_never_moves_sequence_backwards` assertion expects the old expiration timestamp, while the current Milestone 7 behavior intentionally renews the authenticated resume window. The test and documented behavior must be reconciled.
- Pass: no app crash, ANR, `FATAL EXCEPTION`, TLS digest regression, or memory failure was observed during this run.

The Gradle connected-test task clears target application data before instrumentation. The original Mobile device ID, enabled features, language, continuous-ecosystem state, and normal Wi-Fi connection were restored after the run. Future full instrumented runs on the primary phone must first export settings or use a disposable Android profile.

#### Physical connection scenarios

| Case | Result | Observation |
| --- | --- | --- |
| DSK-01 | Pass | Android and Windows discovered one another over BLE in both interfaces. |
| First debug pairing | Fail, reproducible | Windows completed trust while Android remained in `AUTHENTICATING`. A clean app restart followed by another attempt was required. |
| Trusted retry | Pass once | One TLS session formed with a two-member Wi-Fi Direct group. Android used `192.168.137.119`; infrastructure Wi-Fi remained active at `192.168.100.157`. |
| DSK-06 | Pass | Mutual TLS/ALPN completed; no Android Keystore `INCOMPATIBLE_DIGEST` error returned. |
| DSK-07 | Pass | The secure session remained active beyond 60 seconds and retained its established TCP connection. |
| REC-01 | Pass | With Android in the background for more than 70 seconds, the secure session and P2P group stayed active. |
| REC-05 | Pass with note | Turning Android Bluetooth off for 25 seconds did not interrupt the already-established TLS/P2P session; Bluetooth was restored afterward. Desktop's BLE label did not update live. |
| REC-06 / DSK-08 | Fail | Force-stopping Mobile removed the secure session. Relaunching did not reconnect automatically, and the first manual retry stayed in `AUTHENTICATING`; the advertised 24-hour protected resume did not complete. |
| Wi-Fi coexistence repeat | Fail, inconsistent | A later Wi-Fi Direct attempt displayed Android's system warning that normal Wi-Fi would be temporarily disconnected. `wlan0` dropped once and required manually selecting the saved Wi-Fi network. Later attempts preserved `wlan0` but Windows reported `Falha ao aceitar o par Wi-Fi Direct`. |
| Second Android / 3 devices | Blocked | Only one Android device was available. Android-to-Android regression, 1:N routing, coordinator failover, and all-to-all delivery remain unexecuted. |

#### Application-feature interoperability

The secure channel itself opened, but application envelopes are not interoperable between the current Mobile and Desktop implementations:

- Mobile signs only `SHA-256(payload)` for `origin_authentication`; Desktop verifies a signature covering all immutable transport-envelope fields and therefore rejects Mobile messages as `invalid_origin_authentication`.
- Desktop encrypts `VeyroMessage` bytes with `ApplicationPayloadCipher`; Mobile currently attempts to parse `encrypted_payload` directly as a plaintext protobuf.
- Mobile accepts incoming Desktop envelopes without verifying `origin_authentication`, which is also a security defect.

Observed consequence: Desktop-to-Mobile ping reached Mobile and produced local command feedback, but the response was rejected by Desktop. Battery/connectivity remained at “waiting”, and no bidirectional feature can be marked end-to-end pass. File transfer, clipboard, links, notifications, media, presentation, commands, contacts, remote input, drawing tablet, and remote folders are therefore **blocked by the shared envelope contract**, not independently approved or failed.

#### Interface update during the run

The Mobile Resources page was reorganized into a two-column feature grid inspired by the supplied reference. Each enabled feature has a large icon button; tapping it replaces the grid with only that feature's controls, and “Voltar aos recursos” returns to the grid. Multiple-device selection remains visible only when more than one endpoint is connected. The Debug APK containing this layout was installed locally and passed compilation, unit tests, and lint. It was not uploaded.

#### Required retest order

1. Unify transport-envelope signing and application-payload encryption on Mobile and Desktop, including inbound signature verification on Android.
2. Fix the first-attempt BLE confirmation race and make trusted reconnection automatic.
3. Diagnose Windows `WiFiDirectDevice.FromIdAsync` failures and eliminate any path that disables infrastructure Wi-Fi.
4. Repeat ping, battery/connectivity, clipboard, small file, links, media, notifications, commands, presentation, contacts, remote input, and shared-folder exchanges in both directions.
5. Repeat the full Android-to-Android and three-device matrix when the second phone is available.

## Pass criteria

The build is acceptable for continued alpha development when:

- there are no crashes, ANRs, or memory failures;
- pairing consent and Trust Hub rules are never bypassed in non-debug builds; debug-only bypasses must remain compile-time guarded and visibly identified;
- both devices converge on the same connected/disconnected state;
- connectivity changes and ping samples recover after interruptions;
- disabling one module does not disable unrelated modules;
- all transferred test files match their source files;
- no high-severity privacy or permission issue is observed.

## Result classification

- **Pass:** expected result observed on both devices.
- **Pass with note:** correct result with a minor visual or timing issue.
- **Fail:** wrong state, missing data, security/privacy problem, crash, or unrecoverable operation.
- **Blocked:** external restriction prevents execution; record the restriction and continue with independent cases.

## Analysis after execution

After the session, produce a report containing:

1. build commit and device matrix;
2. pass/fail/blocked totals by section;
3. latency summary by power mode;
4. reconnection times for each interruption scenario;
5. crash, ANR, and memory findings;
6. defects ordered by severity and reproducibility;
7. fixes that can be implemented immediately;
8. cases that must be repeated after fixes.

Keep raw logs out of Git when they include device-specific or personal data. Add only sanitized findings and reproducible defect descriptions to the repository.

## Retest addendum — 2026-08-21

This addendum supersedes the obsolete interoperability findings above where noted.

- **Pass — mandatory PIN:** all Debug auto-confirmation paths were removed from Mobile and Desktop. A new pairing now stops at the six-digit comparison screen and requires explicit approval on both devices. Trusted reconnection remains automatic and does not request another PIN.
- **Pass — transport security contract:** Mobile and Desktop now use the same immutable-envelope signature, P-256 ECDH/HKDF derivation, recipient-specific AES-GCM payload, AAD, and P1363 signature format. Mobile verifies the origin signature before decryption and rejects replayed or out-of-sequence envelopes. The former “application envelopes are not interoperable” finding is resolved in code and unit tests; physical feature exchange remains blocked until a direct data link forms.
- **Pass — process restoration:** after reinstalling the Debug APK with settings preserved, Mobile recreated its foreground service and BLE GATT server without toggling the ecosystem switch. Desktop then displayed `2311DRK48I autenticado novamente`.
- **Pass — BLE trust resumption:** Desktop initiated the reliable GATT direction, both sides completed the challenge/proof exchange, and no PIN was shown for the already trusted pair.
- **Pass — infrastructure Wi-Fi preservation:** after the failed Wi-Fi Direct negotiation, Android remained connected to its existing access point at `192.168.100.157`; `mTemporarilyDisconnectedWifi` remained `false`.
- **Blocked — Windows Wi-Fi Direct acceptance:** Windows still reported `Falha ao aceitar o par Wi-Fi Direct`; Android reported no formed group, so there were zero secure fast-channel sessions.
- **Blocked — end-to-end feature matrix:** ping, connectivity, clipboard, files, links, media, notifications, commands, presentation, contacts, remote input, drawing tablet, and shared folders must be repeated after Wi-Fi Direct formation succeeds.

Build verification after the fixes: `testDebugUnitTest`, `assembleDebug`, and `lintDebug` all completed successfully. The corrected Debug APK was installed locally only; it was not uploaded.
