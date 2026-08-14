# Clocky implementation backlog

This backlog separates **GitHub/Web-validated implementation** from work that still requires device/reference verification. GitHub `main`, current source/tests and required Actions checks are the source of truth for implementation status.

## F0 — repository / upstream foundation — complete

- [x] Pin AOSP DeskClock `android-17.0.0_r1` / commit `1f6ebf36d0c14f5e16265d80022cb6068d97cebd`.
- [x] Vendor the full AOSP snapshot under `third_party/aosp-deskclock/`.
- [x] Document Apache-2.0 handling and Google-reference boundaries.
- [x] Keep the proprietary Google Clock APK outside the source tree.
- [x] Preserve the previous Clocky MVP source bundle for migration reference.

## F1 — AOSP standalone direct-port — buildable on GitHub Actions

- [x] Port AOSP source/resources/assets into `app/` while preserving immutable upstream provenance.
- [x] Translate AOSP build dependencies into standalone Gradle dependencies.
- [x] Keep source namespace `com.android.deskclock` and separate install identity as `com.stupidsavacan.clocky`.
- [x] Move provider authority to `${applicationId}` and adapt Manifest/build assumptions to AGP.
- [x] Resolve dependencies, compile, unit-test, lint, `assembleDebug`, and hash the APK in Current App CI.
- [x] Keep artifact-upload quota failures nonfatal when build/test/lint/assemble/hash succeed.

### F1 runtime/device follow-up

- [ ] Review targetSdk 35 runtime-sensitive permission/Manifest behavior on device.
- [ ] Validate direct-boot/backup/provider behavior as a non-system app.
- [ ] Run real-device functional smoke testing.

## F2 — Google Clock UX specification — pre-build contract complete

The app/navigation, Alarm, Clock, Timer, Stopwatch, settings-separation, motion, and parity definition-of-done contracts are documented.

### F2 device/reference follow-up

- [ ] Freeze exact geometry/color/type/radius constants from allowed reference captures.
- [ ] Verify dark/light, RTL, locale and font-scale states on device.

Do not guess missing Google Clock measurements from Web-only work.

## F3 — Widget parity specification — pre-build contract complete

Digital, Digital Stacked, Digital Cities, Analog, Stopwatch Widget, resize/update/accessibility/restore, data ownership and click-role contracts are documented.

### F3 runtime follow-up

- [ ] Measure exact Google reference breakpoints.
- [ ] Implement Stacked/Cities/Stopwatch families when their exact contracts are actionable.
- [ ] Verify click destinations and launcher restore/rebind behavior on an allowed device workflow.

## F4 — Clocky customization model — model/persistence complete; source-defined Digital path integrated through PR #29

- [x] Schema-versioned per-`appWidgetId` `WidgetSettings` JSON store.
- [x] Time/date font family, requested weight, size, letter spacing, color/opacity, X/Y and alignment fields.
- [x] Time hour-mode/leading-zero fields and date format field.
- [x] Background color/opacity/radius/padding fields.
- [x] 4×2 / 4×1 nullable profile overrides and Google-like preset identity.
- [x] Requested→effective Font Weight resolution.

### F4 Digital renderer/editor — implemented

- [x] Wire per-widget settings store into the Digital provider.
- [x] Render independent time/date weight, date visibility, size and letter spacing.
- [x] Resolve 4×1 / 4×2 nullable overrides with `null = inherit` for supported fields.
- [x] Render independent X/Y offsets via RemoteViews translation on API 31+; preserve requested settings with effective 0dp fallback on API 23–30.
- [x] Normalize/sanitize non-finite base/profile X/Y values without destroying nullable inheritance.
- [x] Provide settings UI for weight, date visibility, size and letter spacing.
- [x] Add regression tests for the merged resolver/editor/renderer/model paths.
- [x] PR #27: support the repository-MVP's five proven legacy font families for time/date without replacing the existing requested-weight path.
- [x] PR #28: apply explicit widget hour modes: follow-system leaves the existing TextClock formats intact, forced 12h uses `h:mm`, forced 24h uses `HH:mm`.
- [x] PR #29: delete consolidated per-widget settings when the launcher removes that widget, preventing orphan settings from surviving widget deletion.

### F4 remaining Web-safe candidates — blocked unless semantics are defined

- [ ] X/Y offset editing UI: renderer/model exist, but repository does not define range/interaction semantics.
- [ ] Date-format renderer/editor: current `DateSettings.formatPattern` is non-null with default `EEE, MMM d`, while the provider currently derives its default from locale; there is no explicit “no override” state.
- [ ] Color/opacity rendering/editing: current contracts do not define how ARGB alpha composes with the separate opacity field.
- [ ] Alignment rendering/editing: legacy values mean START/CENTER/END, while the current enum is LEFT/CENTER/RIGHT; exact RTL semantics are not defined.
- [ ] Background rendering/editing: exact transparent/dark/light mapping, fallback colors, padding/radius and API fallback semantics are not fully defined.
- [ ] Leading-zero behavior: the current model has a field, but the old MVP persisted no leading-zero key; do not synthesize migration behavior.
- [ ] Requested/effective weight disclosure UI, if a concrete UI contract is added.

### F4 device-only verification

- [ ] Verify Digital customization appearance in real launcher hosts.
- [ ] Verify RemoteViews X/Y translation/clipping and API 23–30 fallback visually.
- [ ] Verify resize/profile transitions and touch/config UX on device.

## F5 — existing MVP integration — source persistence contract decoded; importer intentionally deferred

- [x] Define ownership of old per-widget presentation settings versus AOSP domain state.
- [x] Retain independent time/date styling and size-profile concepts without duplicating timer/stopwatch state.
- [x] Decode the authoritative retained source bundle `ci/Clocky_MVP_source.zip` (14,299 bytes; Git blob `6d63be2463ec408824a9b06eec282153c1a2df55`).
- [x] Verify legacy SharedPreferences file `clocky_widgets`, prefix `w_<appWidgetId>_`, all saved key names/types, enum mappings, defaults, save/delete behavior, font array, date-pattern array, and absence of profile-specific persistence.
- [x] Verify there is **no old persisted `leadingZero` key**.
- [ ] Implement one-time primitive SharedPreferences import into `WidgetSettings` only after all persisted old semantics are losslessly representable.

### F5 exact legacy key contract

- integers: `timeFont`, `dateFont`, `timeSize`, `dateSize`, `timeX`, `timeY`, `dateX`, `dateY`, `align`, `bg`, `hourMode`, `dateFormat`
- boolean: `showDate`
- strings: `timeColor`, `dateColor`
- no legacy profile keys; no leading-zero key

### F5 importer rules once semantics are resolved

1. Existing consolidated JSON in `clocky_widget_settings` / `widget.<id>.settings` always wins.
2. Read exact legacy keys/types; never guess missing values or invent profile data.
3. Normalize and save into the current JSON store.
4. After successful import, remove or permanently ignore that widget's old keys so deleted/edited values cannot be resurrected.
5. Never dual-write current values to the old primitive key space.

The remaining blocker is **representability**, not source readability: date-format default/override state, color/opacity composition, START/END versus LEFT/RIGHT RTL meaning, and exact old background-layout mapping remain under-specified in the current contract. A partial/lossy importer is intentionally not added.

## F6 — GitHub build/test gate — complete for current main

Current App CI is the canonical Web build environment and continuously covers dependency resolution, compile, unit tests, lint gate, `assembleDebug`, and APK SHA-256. Reference APK Guard runs independently. A green GitHub build does **not** imply launcher/device parity.

## F7 — runtime functional parity

- [ ] Alarm scheduling/firing/snooze/dismiss.
- [ ] Timer state, expiration and notifications.
- [ ] Stopwatch + laps/background state.
- [ ] World clocks/city selection/time-zone changes.
- [ ] Digital/Analog baseline widgets on device.
- [ ] Google-parity app UI and remaining widget families.
- [ ] Remaining Clocky typography/layout controls after their contracts are explicit.
- [ ] accessibility + RTL + font-scale device verification.
- [ ] real-device side-by-side verification.

## F8 — release discipline

- Keep the established Clocky signing identity for upgrade-compatible releases.
- Do not commit signing passwords/JKS to branches.
- Produce signed/release artifacts only after reviewed source, build checks and runtime parity validation.

## Current definition of success

The standalone app resolves dependencies, compiles, tests, lints, assembles a debug APK and records its hash in GitHub Actions. The source-defined Digital customization path now covers weight, date visibility, size, letter spacing, X/Y rendering, the retained MVP's five font families, explicit hour mode, and widget-settings lifecycle cleanup.

The remaining clear work is either contract/specification work or device/reference verification. Web-only work must not invent the unresolved date/color/alignment/background semantics or describe CI as physical-device verification.