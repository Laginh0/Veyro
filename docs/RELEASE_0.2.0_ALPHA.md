# Veyro Alpha 0.2.0

## Highlights

- Every ecosystem feature now opens as a dedicated full-screen page instead of expanding inside the feature list.
- Feature pages share a responsive visual system with a clear identity header, target-device context, status treatment, rounded control surfaces, and screen-safe spacing.
- Media continuity keeps its redesigned player, playback progress, artwork, remote notification controls, output routes, and individual audio-stream volumes.
- Multi-device target selection is more compact and keeps the selected peer visually explicit.
- Portuguese and English copy remains available through the in-app language setting.

## Security

- Persistent P-256 identities and bilateral PIN confirmation protect initial Nearby pairing.
- Signed identity claims bind reconnections to the current connection authentication digits.
- Signed and encrypted transport envelopes now protect application traffic across Nearby, Desktop, and relay routes.
- Replay metadata, route failover guards, logical-peer authorization, authenticated file hashes, and fail-closed key-collision checks were strengthened.
- Bouncy Castle 1.84 replaces the affected 1.79 dependency.

See the [security improvement report](https://github.com/Laginh0/Veyro/blob/v0.2.0-alpha/docs/SECURITY_IMPROVEMENTS_0.2.0_ALPHA.md) for guarantees and open risks.

## Validation status

- 59 Android unit tests passed with no failures or errors.
- Android Lint completed with no fatal findings or errors.
- Debug and release variants assembled successfully.
- The debug APK was installed and opened on an Android 16 device as version `0.2.0-alpha` (`versionCode` 20).
- The release manifest remains non-debuggable and bilateral PIN confirmation remains enabled.
- Physical multi-device failover and live cross-device feature validation remain part of the hardware test plan.

This prerelease is intended for testing and uses the existing development signing key.
