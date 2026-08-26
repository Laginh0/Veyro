# Security improvements in Veyro 0.2.0-alpha

Release date: 2026-08-26

Audit baseline: `SECURITY_AUDIT_2026-08-24.md` reviewed against the Android workspace and the Desktop interoperability implementation.

## Scope

This release hardens the security boundary shared by Google Nearby Connections, the direct Android-to-Desktop channel, and Desktop-mediated Android routing. It does not claim production readiness or exactly-once delivery.

## Improvements included

- Nearby identity is now based on a persistent Android Keystore P-256 key, rather than an endpoint ID or display name.
- First pairing requires bilateral PIN confirmation and pins the peer key. Reconnection requires the pinned fingerprint plus a signed identity claim bound to the current Nearby authentication digits.
- Nearby and Desktop relay traffic use the same signed and end-to-end encrypted `TransportEnvelope` format.
- Message ID, sender epoch, sequence number, expiry, destination, and hop budget are validated across transport changes.
- Persistent permissions are indexed by logical peer identity instead of mutable display names.
- A Desktop coordinator may advertise only Android identities already pinned by the receiving Android device. The coordinator has routing authority, not identity authority.
- Wi-Fi Direct loss can trigger transport failover even when the normal socket callback does not arrive.
- Stale Fast Channel callbacks are prevented from removing a newer active route.
- A reused device ID with a different public key fails closed in Trust Hub.
- Android and Desktop share versioned Protobuf contracts for transport and application messages.
- Nearby file transfers verify an authenticated SHA-256 digest before the temporary file is promoted to its final name.
- Bouncy Castle was upgraded from 1.79 to 1.84 to include the fix for CVE-2026-5588.
- Remote input state, pending gestures, and stylus deltas are cleared when the connection or route changes.

## Current delivery guarantee

Veyro provides at-most-once handling within the current process and logical replay window. It intentionally does not automatically retry effectful commands when an acknowledgement is lost. Exactly-once delivery is not claimed.

## Known security and resilience gaps

| Finding | Severity | Current status |
| --- | --- | --- |
| VYR-SEC-014 | High | Replay/effect state is not yet committed to a durable transactional journal across a process crash or restart. |
| VYR-SEC-015 | Medium | If one member of a Desktop star fails while the Desktop route remains globally active, fallback is not yet selected independently for that logical peer. |
| VYR-SEC-016 | Medium | Nearby endpoint callbacks do not yet expose a local connection-generation token for discarding every stale lifecycle callback. |
| VYR-SEC-017 | Medium | Android-to-Android file transfer through a Desktop relay does not yet implement chunked resume and transport failover. |

## Security posture

The 2026-08-24 audit rated the current implementation **7.4/10 overall** after the central P0 identity and transport-boundary fixes. That score is an engineering assessment, not a certification. The open findings above remain release blockers for a production-stable security claim.

## Recommended next work

1. Add a bounded, cryptographically protected transactional journal for effectful commands.
2. Select failover routes per logical peer with generation tracking and hysteresis.
3. Add generation tokens to the Nearby lifecycle adapter and adversarial callback-order tests.
4. Unify direct and relayed file transfer around chunks, offsets, hashes, transfer epochs, and a resume manifest.
