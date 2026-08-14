# Clocky implementation backlog

This backlog separates **GitHub/Web-validated implementation** from work that still requires device/reference verification. GitHub `main`, current source/tests and required Actions checks are the source of truth for implementation status.

## F0 — repository / upstream foundation — complete

- [x] Pin AOSP DeskClock `android-17.0.0_r1` / commit `1f6ebf36d0c14f5e16265d80022cb6068d97cebd`.
- [x] Vendor the full AOSP snapshot under `third_party/aosp-deskclock/`.
- [x] Document Apache-2.0 handling and Google-reference boundaries.
- [x] Keep the proprietary Google Clock APK outside the source tree.
- [x] Preserve the previous Clocky MVP source bundle for migration reference.

## F1 — AOSP standalone direct-port — buildable on GitHub Actions

- [x] Copy AOSP functional source into `app/src/main/java`.
- [x] Copy all AOSP resources into `app/src/main/res`.
- [x] Copy AOSP assets into `app/src/main/assets`.
- [x] Preserve `third_party/aosp-deskclock/` as immutable provenance.
- [x] Translate AOSP Soong static-library declarations into Gradle dependencies.
- [x] Keep source namespace `com.android.deskclock` during direct-port to avoid mass import rewrites.
- [x] Separate install identity as `applicationId = com.stupidsavacan.clocky`.
- [x] Remove Manifest `uses-sdk`/`original-package` assumptions owned by AGP.
- [x] Move provider authority to `${applicationId}`.
- [x] Generate static platform/hidden-API risk scan.
- [x] Generate AndroidX/Material dependency/import map.
- [x] Resolve Gradle dependencies in Current App CI.
- [x] Compile the current app with JDK 17 / Android SDK 35 in Current App CI.
- [x] Run unit tests, Android lint + lint gate, and `assembleDebug` in Current App CI.
- [x] Record the debug APK SHA-256 in CI even when Actions artifact storage quota prevents upload.

### F1 runtime/device follow-up

- [ ] Review obsolete/restricted Manifest attributes for targetSdk 35 beyond what compile/lint can prove.
- [ ] Review `READ_EXTERNAL_STORAGE`, exact-alarm, notification and full-screen-intent behavior on targetSdk 35.
- [ ] Validate direct-boot/backup/provider behavior as a non-system app.
- [ ] Run real-device functional smoke testing.

## F2 — Google Clock UX specification — pre-build contract complete

- [x] Global navigation/interaction contract.
- [x] Alarm list/edit/firing state requirements.
- [x] Clock/world-city requirements.
- [x] Timer state machine and interactions.
- [x] Stopwatch + lap requirements.
- [x] Settings separation between core Clock behavior and Clocky customisation.
- [x] Motion recreation rules using public Android/Material primitives.
- [x] Visual measurement sheet and parity definition-of-done.

### F2 device/reference follow-up

- [ ] Freeze exact geometry for every Google Clock reference screen.
- [ ] Freeze actual color/type/radius constants from reference captures.
- [ ] Verify dark/light, RTL, locale and font-scale states on device.

Do not guess missing Google Clock measurements from Web-only work.

## F3 — Widget parity specification — pre-build contract complete

- [x] Digital family contract.
- [x] Digital Stacked contract.
- [x] Digital Cities contract.
- [x] Analog contract.
- [x] Stopwatch Widget contract.
- [x] Shared resize/update/accessibility/restore rules.
- [x] Data-source ownership rules.
- [x] Click-role matrix.
- [x] Host-size-driven layout strategy.

### F3 runtime follow-up

- [ ] Measure exact Google reference breakpoints.
- [ ] Implement Stacked/Cities/Stopwatch families.
- [ ] Verify click destinations against the reference behavior on an allowed device workflow.
- [ ] Launcher restore/rebind testing.

## F4 — Clocky customization model — model/persistence complete; Digital path partially integrated

- [x] Schema-versioned `WidgetSettings` model.
- [x] Per-`appWidgetId` SharedPreferences store.
- [x] Independent time/date font family fields.
- [x] Independent time/date requested Weight `100..900`.
- [x] Independent time/date size fields.
- [x] Letter spacing fields.
- [x] Color/opacity fields.
- [x] X/Y offset fields.
- [x] Alignment and hour-mode fields.
- [x] Background color/opacity/radius/padding fields.
- [x] 4×2 / 4×1 nullable profile override model.
- [x] Google-like preset identity.
- [x] Requested→effective Font Weight resolver for variable/static capabilities.

### F4 Digital renderer/editor — implemented on main through PR #23

- [x] Wire per-widget settings store into the Digital AppWidget provider path.
- [x] Render independent time/date weight.
- [x] Render date visibility.
- [x] Render independent time/date size.
- [x] Render independent time/date letter spacing.
- [x] Resolve 4×1 / 4×2 per-field nullable overrides with `null = inherit` for supported fields.
- [x] Render independent time/date X/Y offsets on API 31+ via RemoteViews translation.
- [x] Preserve requested X/Y settings but use effective 0dp offsets on API 23–30.
- [x] Sanitize non-finite offset values at the renderer boundary.
- [x] Provide settings UI for weight, date visibility, size and letter spacing.
- [x] Add resolver/editor/renderer regression tests for the merged Digital customization path.

### F4 remaining Web-safe candidates — require source-defined semantics before implementation

- [ ] X/Y offset editing UI (renderer/model are present; do not invent range/interaction semantics without repository evidence).
- [ ] Color/opacity rendering and editing, if current source/contracts define exact behavior.
- [ ] Alignment rendering/editing, if current source/contracts define exact behavior.
- [ ] Hour mode / leading-zero rendering/editing, if current source/contracts define exact behavior.
- [ ] Background rendering/editing, if current source/contracts define exact behavior.
- [ ] Surface requested/effective weight when a static font is quantized, if a concrete UI contract is present.

### F4 device-only verification

- [ ] Verify Digital customization appearance in real launcher hosts.
- [ ] Verify RemoteViews X/Y translation and clipping on API 31+ devices.
- [ ] Verify the API 23–30 effective-0dp fallback visually.
- [ ] Verify resize/profile transitions and touch/config UX on device.

## F5 — existing MVP integration design — complete

- [x] Map old per-widget settings to the new Clocky settings owner.
- [x] Retain 4×2 + compact concepts as size profiles.
- [x] Retain independent time/date styling.
- [x] Assign Alarm/Timer/Stopwatch/World Clock state ownership to the AOSP domain layer.
- [x] Forbid widget-only duplicate timer/stopwatch state.
- [x] Preserve signing/release discipline separately from the source port.

## F6 — GitHub build/test gate — complete for current main

Current App CI is the canonical Web build environment.

- [x] Resolve debug runtime dependencies.
- [x] Compile Kotlin/resources/Manifest.
- [x] Run unit tests.
- [x] Run Android lint and enforce the lint gate.
- [x] Assemble the debug APK.
- [x] Hash the debug APK.
- [x] Keep artifact upload quota failures nonfatal when all build/hash steps succeeded.
- [x] Run Reference APK Guard independently of the current-app build.

A green GitHub build does **not** imply launcher/device parity.

## F7 — runtime functional parity

1. [ ] Alarm scheduling/firing/snooze/dismiss.
2. [ ] Timer state, expiration and notifications.
3. [ ] Stopwatch + laps/background state.
4. [ ] World clocks/city selection/time-zone changes.
5. [ ] Digital/Analog AOSP baseline widgets on device.
6. [ ] Google-parity app UI.
7. [ ] Google-parity widget families.
8. [ ] Remaining Clocky typography/layout controls.
9. [ ] accessibility + RTL + font-scale device verification.
10. [ ] real-device side-by-side verification.

## F8 — release discipline

- Keep the established Clocky signing identity for upgrade-compatible releases.
- Do not commit signing passwords/JKS to branches.
- Produce signed/release artifacts only after reviewed source, build checks and runtime parity validation.

## Current definition of success

The repository has passed the old “pre-build foundation” gate: the standalone app now resolves dependencies, compiles, tests, lints, assembles a debug APK and records its hash in GitHub Actions. The Digital Widget customization path currently includes weight, date visibility, size, letter spacing and model-backed X/Y rendering with regression coverage.

Product success still requires the remaining runtime families/behaviors and real-device/launcher parity testing. Web-only work must keep those device-only claims explicitly unverified.
