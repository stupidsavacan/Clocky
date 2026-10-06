# Clocky implementation backlog

This backlog separates **GitHub/Web-validated implementation** from work that still requires device/reference verification. GitHub `main`, current source/tests and required Actions checks are the source of truth for implementation status.

## Phase 0 — Stabilize (`CLOCKY_END_STATE.md` §13) — verified on API 34 (moto g13); API 23–30 pending

Exit gate: the existing Digital widget can be added, configured, rendered, and resized reliably. The source-side Issue #31 work is done and the full launcher flow passes on the Issue #31 device (moto g13, API 34). The gate is **not** closed until the API 23–30 checks below pass.

- [x] Config Activity crash: `DigitalWidgetConfigActivity` now uses the Clocky-owned `Theme.Clocky.WidgetConfig` (Material3 DayNight, neutral mockup palette, night variant) instead of AOSP AppCompat `Theme.DeskClock.Settings`, which could not resolve Material `Slider` attributes.
- [x] Inflation smoke test: Robolectric `DigitalWidgetConfigActivityTest` (API 23/28/34) inflates the Activity under its manifest theme and checks the AppWidget result contract (missing id / back → canceled, save → OK + id + persisted). It reproduces the Issue #31 `InflateException` without the fix. API 35 is not covered by Robolectric because CI runs JDK 17.
- [x] Edge-to-edge (targetSdk 35): the config screen applies system-bar insets and uses no action bar.
- [x] 4×1 / 4×2 geometry pinned from the retained MVP metadata: min 250×70dp, minResize 250×**40**dp (the MVP's 70dp made 4×1 unreachable on the Motorola launcher; see contract §11), targetCell 4×2 (API 31+), `reconfigurable` (API 28+). Profile boundary unchanged (`OPTION_APPWIDGET_MIN_HEIGHT` ≤ 94dp → 4×1). Contract: `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md` §11. Tests: `DigitalWidgetMetadataTest`, `DigitalWidgetProfileResolverTest`.
- [x] §5.8 contracts fixed in docs/spec (PR #36).
- [x] AOSP exposure cleanup: Home background `#1A237E` → neutral ink `#16161A`; app/launcher/screensaver label "Clock" → "Clocky"; AOSP launcher icon → interim Clocky adaptive icon.
- [x] Next-alarm `PendingIntent`s get `FLAG_IMMUTABLE`. Before this, boot/locale/time/timezone/upgrade broadcasts crashed the process on API 31+.

### Phase 0 verification actually performed

- Unit + Robolectric tests, `lintDebug` (0 errors), and `assembleDebug` pass locally (JDK 21).
- API 35 Pixel 6 emulator with Pixel Launcher: the baseline `main` APK reproduces the Issue #31 crash on a direct launch of the config Activity (`am start`). The fixed APK opens it without crashing, with correct insets. Home shows the neutral surface, and no crash occurs after an upgrade install or a timezone broadcast.

- Physical moto g13 (Android 14 / API 34, `com.motorola.launcher3`, 720×1600 @ 280dpi), real launcher driven over ADB, with evidence from `dumpsys appwidget`, launcher `dumpsys`, event/main logcat, `run-as` settings store, and screenshots:
  - [x] The widget picker lists Clocky Digital as **4x2**. Dragging it to home binds the widget and launches `DigitalWidgetConfigActivity` (`APPWIDGET_CONFIGURE`) without a crash.
  - [x] Back from config → finish without the widget id → `APPWIDGET_DELETED` → no Clocky widget remains, and no settings are persisted.
  - [x] Save → `APPWIDGET_UPDATE` → widget renders time + date. `widget.<id>.settings` is persisted.
  - [x] Fresh-add options reported by the launcher (from relayout targets at density 1.75): portrait 635×455 px ≈ 363×260 dp, landscape 1167×231 px ≈ 667×132 dp. `OPTION_APPWIDGET_MIN_HEIGHT` ≈ 132dp → 4×2 profile. Launcher item `span(4,2) minSpan(3,1)`.
  - [x] Resize 4×2 → 4×1: `APPWIDGET_UPDATE_OPTIONS`, targets 635×213 / 1167×101 px (minH ≈ 58dp → 4×1 profile); the 4×1 `dateEnabled=false` override hides the date. Resize back to 4×2 restores 132dp → 4×2 and the inherited date. Both directions were repeated.
  - [x] Launcher reconfigure button on the existing widget → config → save updates the same `widget.<id>.settings`.
  - [x] Portrait and landscape (launcher home rotation temporarily enabled, then restored): 4×1 and 4×2 render without clipping, and the same profile holds in both orientations.
  - [x] Removing the widget (after the launcher's undo window) → `APPWIDGET_DELETED` → `clocky_widget_settings.xml` is empty.
  - [x] No Clocky entries in the device crash buffer during the session.

### Phase 0 remaining (device / launcher only)

- [ ] Add / configure / render / resize / reconfigure on an API 23–30 device or emulator (no `targetCell`; pre-28 has no `reconfigurable`; pre-31 uses `minWidth`/`minHeight` for the default size).
- [ ] Verify the API 23–25 layer-list launcher-icon fallback renders acceptably.

### Phase 0 AOSP exposure classification (intentionally not changed)

- AOSP Analog widget: an existing AOSP family, kept exposed. Customizing it belongs to Phase 4.
- AOSP red accent `#DA4336` (FAB, action text) and the dark-only Home UI: AOSP app UI. A light/neutral Home design pass belongs to the Google-parity app UI work (F2), not Phase 0.
- Digital widget picker `previewImage` (AOSP image) and the world-city list / autosizer in the Digital path: replaced when Clocky owns the Digital provider (Phase 1A).
- Other AOSP alarm/timer/stopwatch notification `PendingIntent`s without mutability flags: F1 targetSdk runtime work.

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

### F4 remaining Web-safe candidates — contracts defined; implementation pending

The previously missing semantics are now normative in `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md`, derived from `docs/product/CLOCKY_END_STATE.md` §5.8. These items are no longer blocked on product semantics; implementation must follow that contract rather than infer behavior from model fields.

- [ ] X/Y offset editing UI: use template-relative dp offsets, RTL-aware X direction, ±50% size-class bounds, draw-time clamping, and API 23–30 effective 0dp degradation disclosure.
- [ ] Date-format renderer/editor: migrate `DateSettings.formatPattern` to an explicit nullable override where `null = Locale Auto`.
- [ ] Color/opacity rendering/editing: normalize stored RGB alpha to FF and fold legacy/imported ARGB alpha into the separate opacity value.
- [ ] Alignment rendering/editing: use START/CENTER/END semantics; do not expose absolute LEFT/RIGHT behavior.
- [ ] Background rendering/editing: implement None / Solid mapping and the exact legacy transparent/dark/light migration mapping, including 18dp base padding and 10dp compact override.
- [ ] Leading-zero behavior: implement the current End-State behavior; because the old MVP persisted no key, migration must preserve the current default rather than invent a legacy value.
- [ ] Requested/effective weight and other SDK/launcher degradation disclosure in the editor.

### F4 device-only verification

- [ ] Verify Digital customization appearance in real launcher hosts.
- [ ] Verify RemoteViews X/Y translation/clipping and API 23–30 fallback visually.
- [ ] Verify resize/profile transitions and touch/config UX on device.

## F5 — existing MVP integration — source persistence contract decoded; importer contract-unblocked, implementation pending

- [x] Define ownership of old per-widget presentation settings versus AOSP domain state.
- [x] Retain independent time/date styling and size-profile concepts without duplicating timer/stopwatch state.
- [x] Decode the authoritative retained source bundle `ci/Clocky_MVP_source.zip` (14,299 bytes; Git blob `6d63be2463ec408824a9b06eec282153c1a2df55`).
- [x] Verify legacy SharedPreferences file `clocky_widgets`, prefix `w_<appWidgetId>_`, all saved key names/types, enum mappings, defaults, save/delete behavior, font array, date-pattern array, and absence of profile-specific persistence.
- [x] Verify there is **no old persisted `leadingZero` key**.
- [ ] Implement the one-time primitive SharedPreferences import into the current settings model using `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md`; all persisted old semantics now have a defined lossless mapping.

### F5 exact legacy key contract

- integers: `timeFont`, `dateFont`, `timeSize`, `dateSize`, `timeX`, `timeY`, `dateX`, `dateY`, `align`, `bg`, `hourMode`, `dateFormat`
- boolean: `showDate`
- strings: `timeColor`, `dateColor`
- no legacy profile keys; no leading-zero key

### F5 importer rules

1. Existing consolidated JSON in `clocky_widget_settings` / `widget.<id>.settings` always wins.
2. Read exact legacy keys/types; never guess missing values or invent profile data.
3. Normalize and save into the current JSON store.
4. After successful import, remove or permanently ignore that widget's old keys so deleted/edited values cannot be resurrected.
5. Never dual-write current values to the old primitive key space.

Representability is no longer the blocker: `CLOCKY_END_STATE.md` §5.8 and `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md` define date-format default/override state, color/opacity composition, START/CENTER/END RTL semantics, X/Y interaction bounds, and the exact old transparent/dark/light background mapping. The remaining work is importer implementation plus regression/device verification; partial or lossy migration remains unacceptable.

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