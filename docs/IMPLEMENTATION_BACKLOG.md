# Clocky implementation backlog

This backlog separates **completed pre-build foundation work** from the next compile/runtime stages.

## F0 — repository / upstream foundation — complete

- [x] Pin AOSP DeskClock `android-17.0.0_r1` / commit `1f6ebf36d0c14f5e16265d80022cb6068d97cebd`.
- [x] Vendor the full AOSP snapshot under `third_party/aosp-deskclock/`.
- [x] Document Apache-2.0 handling and Google-reference boundaries.
- [x] Keep the proprietary Google Clock APK outside the source tree.
- [x] Preserve the previous Clocky MVP source bundle for migration reference.

## F1 — AOSP standalone direct-port — complete as pre-build work

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

### F1 runtime follow-up

- [ ] Resolve Gradle dependencies in a real compile.
- [ ] Fix compile errors and missing transitive dependencies.
- [ ] Review obsolete/restricted Manifest attributes for targetSdk 35.
- [ ] Review `READ_EXTERNAL_STORAGE`, exact-alarm, notification and full-screen-intent behavior on targetSdk 35.
- [ ] Validate direct-boot/backup/provider behavior as a non-system app.

## F2 — Google Clock UX specification — pre-build contract complete

- [x] Global navigation/interaction contract.
- [x] Alarm list/edit/firing state requirements.
- [x] Clock/world-city requirements.
- [x] Timer state machine and interactions.
- [x] Stopwatch + lap requirements.
- [x] Settings separation between core Clock behavior and Clocky customisation.
- [x] Motion recreation rules using public Android/Material primitives.
- [x] Visual measurement sheet and parity definition-of-done.

### F2 device-measurement follow-up

- [ ] Freeze exact geometry for every Google Clock reference screen.
- [ ] Freeze actual color/type/radius constants from reference captures.
- [ ] Verify dark/light, RTL, locale and font-scale states on device.

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
- [ ] Verify click destinations against the reference APK.
- [ ] Launcher restore/rebind testing.

## F4 — Clocky customization model — pre-build code complete

- [x] Schema-versioned `WidgetSettings` model.
- [x] Per-`appWidgetId` SharedPreferences store.
- [x] Independent time/date font family.
- [x] Independent time/date requested Weight `100..900`.
- [x] Independent time/date size.
- [x] Letter spacing fields.
- [x] Color/opacity fields.
- [x] X/Y offsets.
- [x] Alignment and hour-mode fields.
- [x] Background color/opacity/radius/padding.
- [x] 4×2 / 4×1 profile override model.
- [x] Google-like preset identity.
- [x] Requested→effective Font Weight resolver for variable/static capabilities.

### F4 renderer/editor follow-up

- [ ] Wire settings store into AppWidget providers.
- [ ] Implement config/editor UI controls.
- [ ] Confirm a reliable RemoteViews weight backend.
- [ ] Surface requested/effective weight when a static font is quantized.
- [ ] Add real Google-like baseline values after measurement.

## F5 — existing MVP integration design — complete

- [x] Map old per-widget settings to the new Clocky settings owner.
- [x] Retain 4×2 + compact concepts as size profiles.
- [x] Retain independent time/date styling.
- [x] Assign Alarm/Timer/Stopwatch/World Clock state ownership to the AOSP domain layer.
- [x] Forbid widget-only duplicate timer/stopwatch state.
- [x] Preserve signing/release discipline separately from the source port.

## F6 — next technical gate: compile/error remediation

This is the first unfinished stage and intentionally comes **after** the 12 no-APK phases.

1. Resolve Gradle dependencies.
2. Compile Kotlin/resources/Manifest without producing a release artifact.
3. Triage compile errors by root cause, not by blind suppression.
4. Replace or adapt incompatible targetSdk/public-API behavior.
5. Add tests for core migrations/settings.
6. Only then produce `assembleDebug`.

## F7 — runtime functional parity

1. Alarm scheduling/firing/snooze/dismiss.
2. Timer state, expiration and notifications.
3. Stopwatch + laps/background state.
4. World clocks/city selection/time-zone changes.
5. Digital/Analog AOSP baseline widgets.
6. Google-parity app UI.
7. Google-parity widget families.
8. Clocky typography/layout controls.
9. accessibility + RTL + font-scale.
10. real-device side-by-side verification.

## F8 — release discipline

- Keep the established Clocky signing identity for upgrade-compatible releases.
- Do not commit signing passwords/JKS to a branch intended to become public.
- Produce signed APKs only after reviewed source, build checks and runtime parity validation.

## Current definition of success

The **pre-build foundation is complete** when the repository contains the AOSP direct-port tree, standalone build configuration, static dependency/API analysis, Clocky customization model, Google UI/UX contract, Widget parity contract and MVP integration ownership map. That condition is now met.

Product success still requires compile success and real-device parity testing.
