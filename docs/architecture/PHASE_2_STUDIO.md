# Phase 2 — Studio Fundamentals: design record

> Status: **implemented in the `phase2/studio-fundamentals` branch** (`docs/product/CLOCKY_END_STATE.md` §5, §7, §13).
> The End-State is authoritative; this file records how Phase 2 extends the Phase 1A/1B pipeline and what was
> decided or deferred on the way.

Exit gate: **an advanced user can finely customize a Digital clock without direct canvas manipulation.**

Out of scope (Phase 3+): canvas drag / snap / pinch / pseudo-resize, Square / Large and a general size-class
override UI, My Designs / Favorites, import / export / share, backup / restore, generated picker previews,
Stacked / Analog / World Clock / Stopwatch.

## 1. Pipeline (unchanged)

`Design v2 → DesignResolver → fit → DigitalWidgetComposer → RemoteViews → launcher / PreviewHost`.
Studio edits the **requested** model; the resolver derives the **effective** spec; the composer renders it.
The Studio preview is `PreviewHost`, i.e. the exact RemoteViews the widget receives. No second renderer.

## 2. Model additions (additive keys of schema 2)

No schema bump and no migration: every new key is optional, defaults reproduce the Phase 1A/1B output
(`DigitalDesignCodecPhase2Test` decodes all 8 built-ins with the new keys stripped and checks equality), and a
pre-Phase-2 app ignores the keys. A requested value is never rewritten by degradation.

| Slot | Addition |
|---|---|
| Date | `uppercase` (default true = what Phase 1A/1B always did) |
| Info | `InfoElement(source NONE / NEXT_ALARM / SECOND_TIMEZONE, style, timeZoneId, label)` |
| Background | `GRADIENT` and `OUTLINE` types, `gradientEnd`, `gradientAngleDeg`, `borderWidthDp` |
| Behavior | `amPm(mode HIDDEN / SUFFIX, scale 0.25..0.6)`, `showSeconds`, `tap(time, date, info)` |
| Effects | `shadow` `CLASSIC / OFF / SOFT / STRONG` (`CLASSIC` = the AOSP shadow every earlier design shows) |
| Fonts | six bundled families, see section 5 |

Leading zero stays in `TimeElement` (existing key); Studio presents it under Behavior.

## 3. Editing session (`studio/`)

* `EditSession(baseline, reference)` holds the **draft** `DigitalDesign` and an undo/redo history of
  `(id, before, after)` entries, 50 deep. `DigitalDesign` is a tree of immutable data classes, so a snapshot is a
  reference and equality is structural: no JSON round trip. A slider drag is one step (`apply(id, key)` coalesces
  while the key repeats; `endGesture()` closes it). A no-op edit records nothing.
* `DesignEdits` is a set of pure `DigitalDesign -> DigitalDesign` functions (one per user operation), with an
  `EditScope`: `ALL` writes the base value, a size class writes that class's `LayoutPatch` only (existing
  Strip / Card patches). Phase 3 canvas gestures can call `session.apply` with the same shape.
* Reset: per slot (`resetSlot`) and everything (`resetAll`), both undoable. The reference is the built-in design
  the draft came from (`source.builtinId`), else the design Studio opened with. Layout reset also restores
  alignment/offsets; Behavior reset also restores leading zero and theme mode.
* `prepare()` folds the Quick Tune text-size step into real base sizes (so the numbers on screen are true) and keeps
  palette, fonts and theme mode as tokens: Material You / Follow system survive a detailed edit. (The Phase 1A
  editor flattened all tokens.)
* Cancel / Back never writes. With unsaved edits it asks (`isDirty`); Save writes through the existing store and
  updater and returns `RESULT_OK`.
* Recreation: the session lives in a `ViewModel` (draft, undo history, tab, mode, scope, preview class survive
  configuration change). After process death, baseline + draft + UI state come back from saved state; **undo history
  does not** (bundle size).

## 4. Studio UI (`widget/studio/`)

`StudioActivity` (replaces `DigitalWidgetAdvancedActivity`; same extras and result codes, so the Gallery / Quick Tune
host is unchanged). Pinned: top bar (back, undo, redo, overflow, save) and the live preview. Below: a horizontally
scrolling chip row `Time | Date | Info | Background | Layout | Behavior` (a "•" marks a slot that differs from its
design), a Basic / Advanced toggle, an "Only for <Strip|Card>" scope switch (Time, Date, Layout), a Checks list and the
slot panel. Landscape puts preview and controls side by side. Every control is at least 48dp (asserted in a Robolectric
test for all slots, Basic and Advanced). Sliders are draggable and the value is tappable for numeric entry (validated,
clamped; a typed value outside the slider's step grid is kept exactly).

Basic vs Advanced follows End-State §5: gradient / outline, padding, border, shadow, letter spacing, custom date
pattern, offsets, leading zero, seconds, AM/PM size are Advanced.

## 5. Rendering additions

* **Info line**: one more slot row (`clocky_info_slot`) in every template, shown on Card only (a requested Info on
  Strip, or with the Minimal template, is kept and disclosed). It is always a `TextClock` fragment, so every font face
  works: next alarm is a *literal* pattern of the text AlarmManager reports (`Utils.getNextAlarm`), second timezone is
  a real clock with `setTimeZone` that keeps ticking in the host. The next-alarm text is refreshed on
  `ACTION_NEXT_ALARM_CLOCK_CHANGED` and from `AlarmStateManager.updateNextAlarm`: still **no periodic app update**.
  The editor shows a sample when no alarm is set (and says so); the widget shows nothing.
* **AM/PM**: a second smaller `TextClock` (`a`) after the time, empty on a 24-hour system clock; with Force 24-hour the
  request is kept, nothing is drawn. **Seconds** extend the formats with `:ss` (host redraws each second; the editor warns).
  `DigitalWidgetFit` measures both extras and the worst-case seconds width.
* **Rendered background**: gradient / outline are drawn by `RenderedBackground` into one bitmap at the widget's real
  size (budget 1.2 MP, scaled down above), only when RemoteViews are built (size, theme, setting changes). Colors of a
  bitmap cannot follow day/night or Material You between updates, so a theme-bound color is resolved for the current
  night mode and disclosed (`Degradation.RenderedBackgroundStatic`). Not done: 3 gradient stops, radial gradients, a
  border on filled types.
* **Legibility shadow**: `setShadowLayer` is not remotable, so each (family, date-caps, shadow) is a pre-built fragment
  layout: `tools/fonts/generate_face_layouts.py` generates 144 layouts, the shadow styles and `FontFragmentTable.kt`.
  SOFT / STRONG pick a black shadow for light text and white for dark text. `CLASSIC` (the default) keeps the
  Phase 1A/1B layouts byte-identical in behavior.
* **Fonts (12 families)**: system sans + the five platform aliases + six bundled OFL families under `res/font`
  (Poppins 300-700, Barlow Condensed 300-700, IBM Plex Mono 300/400/500/700, DM Serif Display, Bebas Neue, Varela Round,
  all unmodified from google/fonts; licenses in `third_party/fonts/`, 2.1 MB). `FontCatalog.families` records each
  family's real weights; any other request resolves to the nearest real face and is disclosed
  (`WeightApproximated(fontId)`), never presented as exact. `res/font` in RemoteViews needs API 26: below that the system
  sans is shown with `FontFallback(bundled-font-needs-api-26)`. None of the bundled fonts covers CJK; the Date panel
  says kanji use the system font (`FontCatalog.Family.coversCjk`). Quick Tune's six categories still map to the Phase 1B
  platform pairs (changing built-in designs is Phase 5).
* **Tap zones**: `TapIntents` gives the widget root its old "Open Clocky" target and a zone intent only where a zone's
  action differs (a design that never touches Behavior keeps one click target). Actions: Open Clocky, Alarms, Timer,
  Stopwatch (via `DeskClock.EXTRA_SELECT_TAB`), Calendar, Edit widget, Do nothing (a no-op broadcast). Defaults keep today's
  behavior: Time and Date open Clocky, Info opens Alarms. (End-State's "Date defaults to Calendar" is not applied: it
  would change every placed widget.)
* **Contrast** (`ContrastChecker`): large text (time) 3:1, small text (date, info) 4.5:1, text opacity included.
  Three honest outcomes: `KNOWN_POOR` (against an opaque card the design draws), `LIKELY_RISK` (translucent card, or
  the wallpaper's dominant color from `WallpaperColors` on API 27+), `UNKNOWN` (no background of its own and no hint:
  Clocky cannot read the wallpaper and says so once). A "Fix" button sets black/white text.

## 6. Compatibility

Component identity unchanged. All 8 built-ins and a Phase 1A design decode and resolve unchanged; the existing
unit and Robolectric suites pass unmodified (only the three removed Phase 1A editor classes and their tests are gone,
see below). Widgets placed by the Phase 1B build kept rendering after installing this build over it (API 35 emulator),
and reconfiguring one opened Quick Tune and Studio on its saved design.

Removed as superseded: `DigitalWidgetAdvancedActivity`, `StyleControls`, `WidgetProfile{Weight,Size,DateVisibility}Editor`,
`SizeClassPatches` and their layouts/tests. Their behavior (per-class overrides, revert) lives in `DesignEdits`
(`DesignEditsTest`) and `StudioActivityTest`.

## 7. Deferred / limitations

* Info: icon and "only within 24 h" (needs a scheduled update), free text, any other provider.
* Date lower-case, "link Time and Date" editing, per-property reset dots (slot and revert-override resets exist).
* Rendered background: 3 stops, radial, border on filled types; day/night for bitmaps (needs re-render).
* Bundled fonts for the 24-family target and kit re-pairing (Phase 5); time-hours/minutes split styling, separators.
* Undo history across process death.
* Template layouts other than Time first (`layout-v31`) have no `clipToOutline` variant: pre-existing, noted for Phase 3.
