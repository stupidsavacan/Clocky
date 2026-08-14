# Twelve-phase pre-build ledger

- [x] 1. Merge AOSP vendor PR into `main`
- [x] 2. Mirror vendored AOSP functional source into `app/`
- [x] 3. Create standalone Gradle configuration from AOSP Soong deps
- [x] 4. Copy/adapt Manifest for standalone application identity
- [x] 5. Mirror and count-verify AOSP resources/assets
- [x] 6. Run static platform/hidden-API risk scan
- [x] 7. Generate AndroidX/Material dependency map
- [x] 8. Add Clocky settings model + schema-versioned per-widget store
- [x] 9. Add requested Weight 100–900 resolver/render contract
- [x] 10. Freeze pre-build Google Clock UI/UX parity contract
- [x] 11. Complete five-family Widget parity specification
- [x] 12. Map existing MVP capabilities into AOSP + Clocky ownership

## Boundary

These twelve phases are the work that can be completed **without generating an APK**. They do not claim compiler success or real-device parity. The next gate is compile/error remediation, followed by debug APK and device testing.
