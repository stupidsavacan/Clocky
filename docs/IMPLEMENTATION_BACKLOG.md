# Clocky implementation backlog

This backlog separates **GitHub/Web-validated implementation** from work that still requires device/reference verification. GitHub `main`, current source/tests and required Actions checks are the source of truth for implementation status.

## Phase 0 — Stabilize (`CLOCKY_END_STATE.md` §13) — complete

Exit gate: the existing Digital widget can be added, configured, rendered, and resized reliably. The gate is closed. The full launcher flow passes on the Issue #31 device (moto g13, API 34, Motorola Launcher3) and on representative pre-Android-12 emulators (API 25 and API 30, Pixel Launcher). This is not a full launcher matrix. One Samsung, Nova, or Lawnchair host and API 26–29 remain unverified, and the End-State §14 launcher matrix still applies to later phases.

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

- Pre-Android-12 emulators (2026-10-06, same PR #37 debug APK), Pixel Launcher (`com.google.android.apps.nexuslauncher`), driven over ADB with evidence from `dumpsys appwidget`, provider relayout logs, the `run-as` settings store, and screenshots:
  - API 25 (`google_apis` x86_64, 1080×1920 @ 420dpi):
    - [x] The layer-list launcher-icon fallback (API 23–25) renders as a dark circular clock face labelled "Clocky" in the app drawer.
    - [x] The picker lists Clocky › Digital clock **4×2**. Dropping it on home opens `DigitalWidgetConfigActivity` without a crash.
    - [x] Back → "appWidgetId was not returned" → `APPWIDGET_DELETED`; nothing is persisted.
    - [x] Save → renders time + date. On fresh add the landscape target is 1052×346 px ≈ 132dp, so `minHeight` 70dp still defaults to two rows without `targetCell` → 4×2 profile.
    - [x] Resize 4×2 → 4×1 commits (landscape 152 px ≈ 58dp → 4×1 profile; a 4×1 `dateEnabled=false` override hides the date). 4×1 → 4×2 also commits.
    - [x] Delete clears `widget.<id>.settings`.
    - Pre-28 has no `reconfigurable`, and this launcher offers no reconfigure entry.
  - API 30 (`google_apis` x86_64, 1080×2280 @ 440dpi):
    - [x] The picker lists **4×2**. Add → config, and Back → cancel + `APPWIDGET_DELETED`.
    - [x] The 4×1 "Hide date" set through the config UI is saved. Fresh add has a landscape target of 1413×343 px ≈ 125dp → 4×2 with the date.
    - [x] Resize 4×2 → 4×1 (148 px ≈ 54dp, date hidden) and 4×1 → 4×2 (date back) both commit.
    - [x] Portrait and landscape (launcher home rotation enabled on the emulator) render 4×2 and 4×1 without clipping.
    - [x] Re-entering `DigitalWidgetConfigActivity` for the existing id updates the same `widget.<id>.settings`, and the widget refreshes. Pixel Launcher on API 30 has no reconfigure button (that launcher UI starts with Android 12), so the Activity was launched directly with the existing id.
    - [x] Delete clears the settings.
  - [x] No Clocky entries in either emulator's crash buffer.

### Phase 0 known non-blocking observations

- A new widget saves `date.formatPattern = "EEE, MMM d"` instead of `null` / Locale Auto. This is the F4 date-format item, which Phase 1A's model v2 resolves.
- The widget picker preview is still the AOSP image (white text, hard to see on the light Android 11 picker). It is replaced with the Clocky-owned provider in Phase 1A.
- Launcher restore/rebind (`onRestored`) is not verified. It belongs to backup/restore (Phase 3).

### Phase 0 AOSP exposure classification (intentionally not changed)

- AOSP Analog widget: an existing AOSP family, kept exposed. Customizing it belongs to Phase 4.
- AOSP red accent `#DA4336` (FAB, action text) and the dark-only Home UI: AOSP app UI. A light/neutral Home design pass belongs to the Google-parity app UI work (F2), not Phase 0.
- Digital widget picker `previewImage` (AOSP image) and the world-city list / autosizer in the Digital path: replaced when Clocky owns the Digital provider (Phase 1A).
- Other AOSP alarm/timer/stopwatch notification `PendingIntent`s without mutability flags: F1 targetSdk runtime work.

## Phase 1A — Digital Core (`CLOCKY_END_STATE.md` §13) — complete (PR #39)

Exit gate: Clocky-owned Digital is stable for the 4×1 / 4×2 family, and the editor preview matches the widget because both use the same resolved specification and RemoteViews path. Design record: `docs/architecture/PHASE_1A_DIGITAL_CORE.md`.

- [x] Design model v2 (`design/model`), schema-2 JSON codec, and a one-way v1 → v2 migration written back in place (`design/storage`). Decode failures render defaults and never overwrite.
- [x] `SizeClassResolver`: Strip (`0 < minHeight < 100dp`) / Card. Phase 0 measured heights classify the same way.
- [x] `DesignResolver`: requested → effective (weight per SDK, legacy families exact at 400, RTL-mirrored and clamped X/Y with 0dp before API 31, explicit `h:mm`/`HH:mm`, radius variants before API 31) plus a `Degradation` list.
- [x] Clocky-owned provider (`ClockyDigitalWidgetProvider`), composer (template + per-font fragments via `addView`), fit (one common scale measured on the applied production RemoteViews with worst-case strings), Native background. The AOSP Digital implementation, city list, sizer and Phase 0 renderers are removed.
- [x] The legacy component name `com.android.alarmclock.DigitalAppWidgetProvider` is kept as a one-line alias: renaming deleted placed widgets on update even with `oldName` (API 30 evidence; End-State §12 updated).
- [x] PreviewHost: the editor applies the same `buildPortrait` RemoteViews, with a Strip/Card toggle and degradation notices.
- [x] Editor controls: font, color + opacity, START/CENTER/END, date format (Locale Auto + six literal presets), background (None/Solid, color, opacity, corners, padding).
- [x] Robolectric: composition, reapply idempotence, gravity remotability, background, fit (native graphics), store migration, preview contract, style controls → save (SDK 23/28/34).
- Device evidence (2026-10-06, `google_apis` x86_64 emulators, Pixel Launcher, real launcher driven over ADB):
  - API 30:
    - [x] Phase 0 → 1A in-place upgrade keeps the placed widget and migrates its v1 settings to v2.
    - [x] Strip hides the date per the migrated override.
    - [x] Editor preview = saved 4×2 widget (Amber System time, Mono `yyyy.MM.dd` date END-aligned, Solid Dark 24dp drawable background).
    - [x] TextClock ticked 1:39 → 1:41 with the Clocky process killed: no app update loop.
  - API 35:
    - [x] Fresh add → config (Card preview from the host's real size).
    - [x] Coral Serif + Solid with system-radius outline: preview = widget.
    - [x] 4×2 → 4×1 → 4×2 resize.
    - [x] Launcher pencil reconfigure opens the saved design; setting the Strip date to Hide updates preview and widget.
    - [x] Delete clears the settings.
  - API 25:
    - [x] Fresh add → config; Mint on Navy Solid 16dp (drawable path) renders identically in preview and launcher.
    - [x] Requested weight 600 is stored, and the editor discloses "weight 600 shows as 500".
    - [x] 4×1 resize without clipping.
    - [x] Delete clears the settings.
  - [x] No Clocky entries in any emulator's crash buffer.
  - Physical moto g13 (API 34, Motorola Launcher3, ja-JP; 2026-10-07, driven with `tools/device/cdev.py`):
    - [x] Fresh add → config; Back → "appWidgetId was not returned", no widget or settings left.
    - [x] Save → Amber time, Solid Dark 24dp (API 31+ outline): preview = widget (id 22, schema 2).
    - [x] Resize to one row: the editor's host-derived class is **Strip** (`OPTION_APPWIDGET_MIN_HEIGHT` < 100dp). `cdev`'s "Card (est.)" comes from the portrait host-view height, ~122dp.
    - [x] The launcher pencil reconfigure keeps id 22; setting the Strip date to Hide shows in the preview and the widget; resizing back to two rows brings the date back.
    - [x] Delete → `<map />`; no crash or ANR.
    - Not re-checked here: widget landscape. Motorola home rotation is off and I left the launcher setting alone; the API 30 emulator and the Phase 0 moto session covered it.
  - [x] Found on the moto and fixed: with auto-rotate, the config screen opens in landscape, where the pinned preview left the controls a ~55px viewport. `layout-land` now puts the preview and the controls side by side, each scrolling (Robolectric regression test, SDK 23/28/34; verified on the moto).

- [x] Widget-picker preview (Issue #31 §5): API 31+ pickers render `clocky_digital_widget_preview` (live TextClocks, theme text colors), verified legible in the moto g13 Motorola picker. Below API 31 the picker still uses the AOSP `previewImage`. Replacing it needs a Clocky PNG asset, which fits with the Phase 3 generated-previews work.

### Mockup alignment (PR #38 reference; Phase 1B scope, recorded here)

The 26-board mockup's Clocky Default uses **Center Stack** for Card (date above time, centered) and **Inline** for Strip (time with date beside it), as End-State §5.8 recommends, with a next-alarm row (the Phase 2 Info line). Phase 1A keeps a single `TIME_FIRST` template, matching the Phase 0 look. Choosing templates per size class and the Clocky Default preset's styling (date weight 500 over time weight 300, etc.) belong to Phase 1B (Quick Tune / Kits).

## Phase 1B — Easy Creation (`CLOCKY_END_STATE.md` §6, §13) — implementation complete; wider launcher matrix pending

Exit gate: a first-time user can create an attractive Digital clock in a few taps, starting from a curated design rather than a blank editor. Design record: `docs/architecture/PHASE_1B_EASY_CREATION.md`.

- [x] Design v2 style tokens (palette snapshot, theme mode, font pair, text-size step), built-in provenance, `ColorRef.Token`, five templates (Time first, Center Stack, Inline, Split, Minimal) as additive schema-2 keys; Phase 1A documents decode unchanged.
- [x] Resolver / composer: token colors and fonts, per-class template, Follow system (`setColorInt` day/night) and Material You (`setColor` onto `clocky_dyn_*` aliases of `system_accent*`) on API 31+, disclosed static degradation below.
- [x] Library: 4 kits (Default, Minimal, Bold, Editorial) x 2 designs = 8 immutable, versioned built-ins; widgets receive a snapshot.
- [x] Gallery: live production-RemoteViews cards, kit filter, Clocky Default preselected, direct "Add this clock" (1 tap), "Customize" into Quick Tune.
- [x] Quick Tune: palette (kit palettes + Material You + Follow system), typography (6 categories), layout (per size class), text size S/M/L (labelled "Text size"), date visibility, Surprise me (kit combination table only), "Detailed edit" into the Phase 1A editor.
- [x] Clocky Default is Center Stack on Card and Inline on Strip.
- [x] Fonts: system families only; nothing bundled or redistributed (the bundled OFL library arrived in Phase 2).
- [x] Robolectric: config host (fresh add, direct add, per-card add, cancel, tune, Surprise me, Material You, reconfigure, Phase 1A fallback, recreation), plus pure tests for kits, Quick Tune, resolver theme modes and codec (SDK 23/28/34). `lintDebug`: 0 errors.
- Device evidence (2026-10-07, `cdev`, debug build of this branch):
  - API 35 emulator (Pixel Launcher): launcher drag opens the Gallery (Clocky Default selected); "Add this clock" creates a widget in one tap; Gallery -> Customize -> Mint + Serif + Split -> Done: the widget matches the Quick Tune preview; the launcher reconfigure pencil reopens Quick Tune on the saved design; Material You renders wallpaper-derived colors and flips to light tones with `cmd uimode night yes` **without an app update**; Back from the Gallery leaves no new widget; `am kill` of the Clocky process while a widget is placed: TextClock keeps ticking (11:33 -> 11:34); Strip resize shows Inline.
  - API 30 emulator: Gallery (live cards, inset tiles), Tonal + Material You shows the "needs Android 12" notice and falls back to the design palette; a Phase 1A widget placed earlier still renders.
  - API 25 emulator: Gallery -> Poster + Serif -> Done; the editor discloses "weight 600 shows as 500"; widget equals preview.
  - Physical moto g13 (API 34, Motorola Launcher3, ja-JP, 720x1600; 2026-10-07, `cdev` + scrcpy for unlocking by the owner): launcher drag opens the Gallery with live Japanese dates; Bold Poster -> Customize -> Ocean + Serif -> Done placed widget id 23 and it matches the preview; resize to one row shows the Inline strip; the launcher pencil reopens Quick Tune on the saved design (Strip, Ocean, Serif); Surprise me then Back cancels and the stored design is unchanged (`palette ocean`, `source bold-poster`); no crash or ANR in the log. Not exercised on the moto: Material You / night switch (covered on the API 35 emulator), landscape.
- Found and fixed during implementation: Surprise me dropped Material You (caught by the new tests); Quick Tune preview was taller than the viewport on 2400px screens (preview now scales down uniformly); S/M truncated to "..." (button padding).
- Not verified: launcher matrix beyond Pixel Launcher, API 26-29, font scale 200%, RTL, Quick Tune landscape on a device, Follow system on API 30 across a real day/night switch.

## Phase 2 — Studio Fundamentals (`CLOCKY_END_STATE.md` §7, §13) — implemented; wider launcher matrix pending

Exit gate: an advanced user can finely customize a Digital clock without direct canvas manipulation. Design record: `docs/architecture/PHASE_2_STUDIO.md`.

- [x] Studio v1 (`StudioActivity`, replaces the Phase 1A editor): slots Time | Date | Info | Background | Layout | Behavior, Basic / Advanced, numeric entry on every slider, per-size "only this size" scope with revert, slot and global reset, Undo / Redo (50 steps, drag = one step), draft / Save / Cancel with discard confirmation, recreation-safe.
- [x] Requested -> effective disclosure in the editor (weight approximation per font, bundled font below API 26, offsets, radius, theme, Info not shown, rendered background static).
- [x] Fonts: 12 families (6 bundled OFL, licenses in `third_party/fonts`), real per-family weights, CJK note.
- [x] Time/Behavior: AM/PM suffix, leading zero, seconds, hour mode; tap zones (Time / Date / Info).
- [x] Info line: next alarm (refreshed on alarm change, no periodic update), second timezone with label.
- [x] Rendered backgrounds (gradient, outline) and legibility shadow (Off / Soft / Strong, pre-built layouts).
- [x] Contrast warnings: known / likely / unknown, text opacity aware, wallpaper hint on API 27+, one-tap fix.
- Tests: pure (session, edits, resolver, codec, contrast) and Robolectric (composer incl. every font fragment x caps x shadow, Studio views, 48dp targets, undo/redo, draft/cancel/save, recreation, process-death restore, SDK 23/28/34). `lintDebug`: 0 errors. `assembleDebug` builds.
- Device evidence (2026-10-07, `cdev`, debug build of this branch):
  - API 35 emulator (Pixel Launcher): two widgets placed by the Phase 1B build kept rendering after installing this build; launcher reconfigure -> Quick Tune -> Detailed edit opens Studio on the saved design; Info -> Next alarm shows a sample in the preview (no alarm set) while the widget shows no Info row; Background -> Advanced -> Gradient previews and, after Save, the placed widget shows the same gradient; after creating an alarm in Clocky the widget showed "Wed 7:30 AM" without any app-side polling; tapping the Info row opened Clocky.
  - Physical moto g13 (API 34, Motorola Launcher3, ja-JP, 720x1600; `cdev`): launcher widget id 23 reconfigured via `launch config` -> Quick Tune -> Detailed edit -> Studio renders in Japanese with the live preview; Info -> Next alarm (sample note shown) and Background -> Gradient preview, and after Save the placed widget shows the gradient. The dark Ocean text on the dark gradient was flagged by the contrast check ("may be hard to read", with a Fix button), which is the real-device proof that the warning fires. Found and fixed: the Fix / Reset buttons were created as bare `android.widget.Button` and rendered as magenta text on device (now `MaterialButton`). The widget was reset afterwards (Background reset to the design, Info off). Not exercised on the moto: landscape, Material You / night, tap zones, AM/PM/seconds.
  - moto g13, second pass (bundled-font conclusion superseded by the 2026-10-07 PR #44 audit): originally reported bundled Poppins rendering, but that check did not establish actual launcher Typeface support. The audit now confirms all six bundled weight-400 faces silently render the system face on this host. The editor discloses "weight 900 shows as 700"; Behavior -> 12-hour + AM/PM suffix + seconds render on the placed widget (fit shrinks the date to make room); a Time tap zone set to Timer opens Clocky on the Timer tab when the widget time is tapped; landscape Studio puts preview and controls side by side; Behavior reset returned the widget to 24-hour without AM/PM. Widget left in its original look.
  - API 30 emulator (Pixel Launcher): Studio opens on a Phase 1B design, Outline background renders in the preview, "Material You needs Android 12" disclosure shown.
  - Robolectric additions: Studio under `ar-ldrtl` (RTL) and at 200% font scale (all controls >= 48dp, reset action reachable). Font scale on a device could not be set: `cdev adb` refuses `settings put` by design, so it was not bypassed.
  - API 25 emulator: Studio opens on a Phase 1B design via `launch config`, preview renders, controls usable.
- Found and fixed during implementation: Info slot missing from the API 31+ layout variant; Reset button state went stale during slider drags; the "sample alarm" note needed a post-render rebuild; dialog OK wiring only exists after the show listener runs (test timing, not a bug).
- Not verified (honest list): API 26-29 and 31-33 emulators, launchers other than Pixel Launcher and Motorola Launcher3, Material You / night with a gradient (disclosed static by design), Calendar / Edit-widget / Do-nothing tap zones on a device (Alarms and Timer verified; intents unit-tested), RTL and 200% font scale on a device (Robolectric only), TalkBack, bundled-font rendering on API 26-27.
- [x] Follow-up / PR #44 (owner ruling 2026-10-07): host-aware AM/PM path, localized `a` fallback with `AmPmLocalized` disclosure, requested-value preservation, Preview/widget parity and host ticking. Universal Latin AM/PM is temporarily paused and is **not** a Phase 3 gate; the experimental Latin marker path remains available only for a host that proves it can apply it. Motorola Launcher3/API 34 uses the localized path. Plan/results: `PHASE_3_RESPONSIVE_CANVAS.md` §1a.
- [x] **Phase 2 follow-up 1b — bundled-font host parity (merged as PR #45; 3A-0 prerequisite satisfied):** generalize host capability beyond the AM/PM marker; resolve/disclose ordinary Time/Date/Info remote-font non-support and make Preview/fit use the same effective face as the placed widget. All six bundled weight-400 faces fall back silently on moto g13 / API 34 / Motorola Launcher3; equal sample widths and byte-identical glyph crops confirm it. Unsupported/unknown hosts must use a platform-safe system face with explicit degradation while preserving the requested font id. Other hosts/weights are not yet audited; see `PHASE_3_RESPONSIVE_CANVAS.md` §1b. Done: `HostFontCapability` (one bundled-font flag + AM/PM marker), `RenderEnvironment.supportsBundledFonts`, `REASON_HOST_BUNDLED_UNSUPPORTED` disclosure (one line per font), requested id preserved, Preview/fit/widget share the effective face; moto g13 / Launcher3 verified for all six families (record: `PHASE_3_RESPONSIVE_CANVAS.md` §1b.1). Not exercised: other hosts/APIs; system-font inventory deferred.
- Deliberate deferrals: see `PHASE_2_STUDIO.md` section 7 (Info icon / within-24h, Date lower-case, per-property reset dots, 3-stop / radial gradient, 24-family fonts, undo across process death, Date -> Calendar default).

## Phase 3 — Responsive Canvas & Library (`CLOCKY_END_STATE.md` §7–§9, §13) — 3A-1 merged; 3A-2 implemented, device shipping gates pending

Exit gate: designs can be saved, duplicated and shared, and they work naturally at several sizes. Design record: `docs/architecture/PHASE_3_RESPONSIVE_CANVAS.md`. The open semantics were ruled on 2026-10-07 (record section 8) and END_STATE was amended accordingly (record section 9). 3A-0 fixed maxW/minH and 100dp / 160dp / 2.5625 for the measured host matrix (record §2.10); 3A-1 implements the pure model; render/platform activation remains 3A-2. 3A and 3B never share a PR.

- Prerequisite
  - [x] Phase 2 follow-up 1b (bundled-font host parity) merged as PR #45; Preview/fit/placed widget resolve the same effective face.
- 3A-0 Measurement — complete 2026-10-08; diagnostic branch never merged
  - [x] Debug-only diagnostic (`ClockySizeDiag` options/candidates; debug-only lowered minResize), formatter/classifier/map instrumentation tests.
  - [x] moto API34 9 portrait, Pixel API35 9 portrait, Pixel API30 12 portrait + 12 landscape observations; raw table and 222 candidate rows in `docs/measurements/phase3a0/`.
  - [x] Threshold/input decision: maxW/minH; Strip 100dp, Large 160dp, Square ratio 2.5625. All 30 host/cells match intended classes; 4×4 Square by owner clarification; END_STATE §9 amended.
  - [x] API31+ A/B spike: choose A. 30-update final A samples on moto/API35; B exceeds API35 bitmap limit at 4×4 and is slower on moto. Memory, Parcel and compose/fit timings recorded.
  - [x] Live resize: owner-approved premise correction (callbacks during held drag); freeze isolates API35 host-only existing-entry switching. API31+ home rotation not exercised; optional API31–33/unavailable launchers not exercised.
  - [x] Process-absent TextClock ticking on all three hosts; in-place Phase 2 regression and cdev byte-identical settings restore proofs; IDs retained, sessions finished.
  - [x] Current-template local audit: 280 successful geometry cases, 20 SPLIT Strip inflation failures; production minResize remains **NO** until 3A-2 fixes/verifies that template. Preview uses same entry/fit/effective-font path; final four-class layout parity is 3A-2.
  - [x] goAsync decision **YES**; fit+compose entry p95 exceeds 100ms, A update p95 near 400ms. First-update/end-to-end production budget verification remains 3A-2.
- 3A-1 Responsive model / codec / resolver / edit semantics — merged as PR #48 (2026-10-08)
  - [x] `SizeClass` Square / Large and measured `SizeClassRule`: MAX_WIDTH/MIN_HEIGHT, 100dp / 160dp / 2.5625, 30 distinct measured cases and boundary/invalid-input tests.
  - [x] General typed LayoutPatch: permitted Time/Date/Info layout paths, padding and additive nullable Date gap; R2 field-by-field Square/Large→Card→base inheritance; own-only badges and revert.
  - [x] Strip/Card legacy weight kept; Square/Large own weight creation rejected. Imported forbidden/future paths are inert and preserved; schema remains 2 with no migration/built-in version change.
  - [x] Unknown class/path JSON values and unknown base/patch template requests preserved through round-trip and known edits; safe template fallback is disclosed. Quick Tune / Studio preparation and known-layout reset retain future data.
  - [x] Four-class model/edit APIs complete. Provider/Preview explicitly retain Phase 2 Strip/Card render policy; four-class selector UI and final Preview integration deferred to 3A-2.
  - [x] 416 unit/Robolectric tests (including SDK23/28/34), lint and debug assembly green. moto g13/API34 in-place upgrade preserves widget ID 23, byte-identical settings and appearance; no crash/ANR; session finished. PR-safe evidence: `docs/measurements/phase3a1/moto-upgrade-summary.md`.
- 3A-2 Render / platform — implemented; **not shipping-certified** (API35 p95 and alarm boot-crash gates failed)
  - [x] API31+ Keying A: distinct valid host-reported SizeF keys only; API23–30 pair; one MAX_WIDTH/MIN_HEIGHT class per widget and actual entry-specific fit.
  - [x] SPLIT Strip supported TextView spacer; explicit before/after RemoteViews.apply on SDK23/28/31/34/35. Native 300-case audit × SDK28/31/34/35 and 345 fractional-key cases × SDK31/34/35: zero failures.
  - [x] goAsync/finish, expiring single worker, latest pending generation per widget, atomic stale check/send; success/failure/coalescing/deletion/rejected-executor tests.
  - [x] Four-class Preview/Studio/Quick Tune selector; shared resolver/fit/composer; scoped Info/layout/date gap; Square/Large scoped weights absent. R2 Card inheritance; no built-in/schema/component changes.
  - [x] API30 upgrade IDs5/7 and byte-identical settings; pair rotation, resize, process-absent ticking, fresh4×2 placement, four-class Time/Date Preview parity and scoped UI. Debug timing/bitmap/Parcel instrumentation excludes diagnostic apply from timings.
  - [x] Metadata: retain release minResize250×40dp. Candidate110×40dp passed API30 and moto 2×1/2×2/3×1/3×2 after template gate; fresh placement/picker remain4×2. Default minWidth250dp / targetCell4×2 unchanged. Widening deferred while API35 shipping gates remain open.
  - [x] motoAPI34: real4-class Keying A, worst-case4×4 send4,689,984B/Parcel5,212B; n30 per class p95 generation183.81/241.52/251.80/374.68ms; first latency explicitly recorded. Existing3A-1 ID23/settings/geometry restored byte-identically; process-absent ticking9/9. SPLIT+2×1 real apply/display/Preview succeeds. Dedicated cdev session finished.
  - [x] moto real four-class worst-case Preview spec/visual parity; held-drag callback/send before UP, no final stale layout. No moto launcher setting changed for rotation. [Independent API35 final report](https://github.com/stupidsavacan/Clocky/pull/49#issuecomment-6056052332) records rotation, stale rejection, parity, memory/send/ticking/settings and full pixel-layout restoration; emulator running, sessions finished.
  - [ ] API35 p95 performance: independent report Card427.57ms /Square671.30ms versus400ms target. Profile/recheck under stable host resources. Final cold boot also reproduces existing AOSP alarm PendingIntent mutability crash on PR APK: no-crash gate fails; AOSP-domain fix requires owner ruling. Do not lift Draft or declare3A complete.
  - Measurement: `docs/measurements/phase3a2/RESULTS.md`. Phase3A is not complete; Phase3B/3C wait for owner merge.
- 3B Canvas
  - [ ] Selection, drag, snap (center / edges / baselines, haptics, second finger disables snap), 1dp nudge; one gesture = one undo step.
  - [ ] Pinch to resize text; pseudo-resize handle 2×1–5×4 using measured cell sizes.
- 3C Library & Stacked
  - [ ] `DesignRepository` + My Designs (save, duplicate, rename, delete, favorites).
  - [ ] Import / Export (`.clocky`, `CLOCKY2:` text code), share sheet.
  - [ ] Apply to other widgets.
  - [ ] Stacked family: new provider (component name frozen on first release), `STACKED` template.
- 3D Platform integration
  - [ ] Backup enabled for Clocky presentation data (`fullBackupContent` + `dataExtractionRules`); `onRestored` two-phase id remap.
  - [ ] `requestPinAppWidget` from Library / Quick Tune.
  - [ ] Picker previews: generated (API 35+), `previewLayout` (31–34), Clocky-rendered PNG (below 31).
  - [ ] cdev guard: refuse `bmgr restore` / `wipe` / `clear` (tooling PR).
- Known not device-verifiable within cdev rules (see the record's section 7): `onRestored` after a real launcher restore, pinch / two-finger gestures, haptics, TalkBack, font scale 200%, launchers other than Pixel and Motorola.

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
