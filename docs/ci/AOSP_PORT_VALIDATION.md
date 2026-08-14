# AOSP standalone port validation

This branch establishes a standalone Gradle-buildable AOSP DeskClock foundation for Clocky and records the compatibility work needed for the current Android toolchain.

## Validated pipeline

`Current App CI` validates the following on JDK 17 with Android SDK 35 / Build Tools 35.0.0:

- Gradle wrapper integrity
- debug runtime dependency resolution
- Kotlin compilation
- debug unit tests
- Android lint
- debug APK assembly
- APK SHA-256 recording

The final pre-PR application state passed compile, unit tests, lint, assemble, hashing, and artifact upload in Current App CI run `31779022372`.

## Compatibility remediation included

The port contains targeted changes for modern standalone Android builds, including:

- current Android manifest/runtime compatibility
- notification permission-safe notification delivery
- exact-alarm fallback handling where exact privilege is unavailable
- explicit PendingIntent mutability on Android 12+
- mutable PendingIntent only for the RemoteViews collection template that requires fill-in intents
- modern AndroidX/public API replacements for restricted/internal APIs
- lint-safe timer formatting and resource metadata fixes

## Reproducibility

The deterministic remediation scripts under `tools/ci/` are retained as an audit trail and for future upstream refresh work. The one-off self-modifying GitHub Actions workflows used during bootstrap are intentionally not retained on the target branch.

## Remaining work

This validates the standalone functional foundation only. It does not claim real-device behavioral parity with Google Clock. Google Clock parity, Clocky-specific UI, widget customization, and device interaction testing remain separate follow-up workstreams.
