# Phase 1A — Digital Core: design record

> Status: **implementation design for Phase 1A** (`docs/product/CLOCKY_END_STATE.md` §13).
> The End-State is authoritative. This file records how Phase 1A reaches its exit gate from the code
> that existed when Phase 0 closed (PR #37), and the decisions made along the way.

Exit gate: **Clocky-owned Digital is stable for the 4×1 / 4×2 family, and the editor preview matches
the actual widget because both use the same resolved specification and the same RemoteViews path.**

Out of scope (later phases): Gallery / Quick Tune / Kits / Material You (1B), Studio, Info line, tap
zones, Fit-percentage size mode, more fonts (2), Square / Large, responsive `SizeF` maps, backup /
restore, generated previews (3), and other families (4).

## 1. Starting point (Phase 0 code)

| Concern | Phase 0 owner | Phase 1A disposition |
|---|---|---|
| Per-widget settings | `WidgetSettings` schema v1 JSON in `clocky_widget_settings` / `widget.<id>.settings` | Replaced by **Design v2** in the same prefs file and key, with a one-way v1 → v2 migration on load |
| Size profile | `DigitalWidgetProfileResolver` (4×1 / 4×2, `MIN_HEIGHT ≤ 94dp`) | Replaced by `SizeClassResolver` (Strip / Card, `0 < MIN_HEIGHT < 100dp` → Strip) |
| Weight resolution | `FontWeightResolver`, `RemoteViewsFontWeightPolicy` | **Kept** (pure, tested) |
| Legacy MVP families | `LegacyWidgetFontFamilyPolicy` | **Kept**; the families become font fragments |
| Letter spacing / hour mode | `WidgetLetterSpacingPolicy`, `DigitalWidgetFormatPolicy` | **Kept** |
| X/Y degradation | `DigitalWidgetOffsetRenderer.effectiveOffsets` | Logic kept inside the resolver; the RemoteViews half moves to the composer |
| Rendering | AOSP `DigitalAppWidgetProvider` + `digital_widget.xml` (28 TextClocks, world-city list, next-alarm row, AOSP sizer) | Replaced by the Clocky composer: a template plus `addView` font fragments |
| Provider | `com.android.alarmclock.DigitalAppWidgetProvider` | Replaced by `com.stupidsavacan.clocky.widget.digital.ClockyDigitalWidgetProvider`, which declares `android.appwidget.oldName` = the AOSP class so placed widgets survive the update. The AOSP Digital receiver and city service are removed from the manifest. |
| Editor preview | Static TextViews | `PreviewHost`: the same composer applies the same RemoteViews locally |

AOSP domain code (alarms, timers, stopwatch, cities, `DataModel`) is untouched. The AOSP Analog
widget stays as it is (Phase 4).

## 2. Design model v2

```
WidgetInstance(appWidgetId, design)
DigitalDesign
├─ origin: String?               preset identity (v1 presetId, "google-clock")
├─ time: TimeElement             text style + leadingZero
├─ date: DateElement             visible + text style + formatPattern: String? (null = Locale Auto)
├─ background: BackgroundElement type NONE|SOLID, color, opacity, cornerRadius (System|Dp), paddingDp
├─ layout: DesignLayout          template (TIME_FIRST only in 1A) + overrides: Map<SizeClass, LayoutPatch>
└─ behavior: Behavior            hourMode
TextStyle: fontId, weight 100..900 (requested), sizeSp, letterSpacingEm, color: ColorRef, opacity 0..1,
           alignment START|CENTER|END, xDp, yDp
ColorRef: Fixed(rgb) only in 1A (Dynamic/Auto arrive with 1B); stored alpha is always FF
```

The JSON uses `schema: 2`. Override patches are stored as **property path → value** maps
(`"overrides": {"strip": {"date.visible": false}}`), as End-State §9 specifies. In Kotlin they are a
typed nullable `LayoutPatch`.

### Overridable properties

End-State §9 limits overrides to layout properties. **Exception kept for migration:** `time.weight` /
`date.weight` were overridable per profile in v1, and that UI exists. Dropping them would be lossy
and irreversible, so v2 keeps them overridable. Whether weight counts as an un-overridable
"typeface" property is an open product question (see §7); removing it later is a simple one-way
migration.

### v1 → v2 migration

Every value the Phase 0 UI could write, and everything the Phase 0 widget rendered, carries over unchanged. v1 fields that were never rendered or editable map to their v2 meaning (noted per row). Migration is written back as v2 only after a successful decode; a decode failure renders defaults without overwriting the stored JSON.

| v1 | v2 | Why |
|---|---|---|
| `schema` missing / 1 | migrated, then written back as 2 | One-way (End-State §8 Versioning) |
| `presetId` | `origin` | identity |
| `time/date.argb` + `opacity` | `Fixed(rgb)` + `opacity × alpha/255` | Contract §5 alpha folding |
| `alignment` LEFT / CENTER / RIGHT | START / CENTER / END | Contract §2. v1 alignment was never rendered or editable, so no visible behavior changes |
| `date.formatPattern` (non-null, default `"EEE, MMM d"`) | `null` (Locale Auto) | v1 never rendered this field: the provider always used the locale skeleton. Mapping to `null` keeps what the user actually saw. The v1 value was a model default the UI never wrote. |
| `background` argb / opacity / radius / padding | opacity > 0 → SOLID (rgb + folded opacity), otherwise NONE; radius → `Dp(radius)` if > 0, otherwise `System`; padding kept | v1 background was never rendered or editable |
| `fourByOne` / `fourByTwo` | `overrides[STRIP]` / `overrides[CARD]` | The profile → size-class mapping matches Phase 0 device measurements (§3) |
| `time.hourMode` | `behavior.hourMode` | — |
| `fontFamily` / `enabled` / `weight` / `sizeSp` / `letterSpacing` / `xDp` / `yDp` | `font` / `visible` / same | Unknown font ids are kept as requested and fall back to system sans at render (disclosed) |
| `leadingZero` | kept | Contract §8 |

## 3. Size classes (Strip / Card)

- Input: host `OPTION_APPWIDGET_MIN_HEIGHT` (dp), as in Phase 0. One widget keeps one class in both
  orientations, so per-class overrides behave predictably.
- `0 < h < 100` → **Strip**. Otherwise, including missing or zero options → **Card**.
- The boundary moves from Phase 0's 94dp to End-State's 100dp. Every height measured in Phase 0 is
  classified the same way under both rules: 4×1 ≈ 54–58dp, 4×2 ≈ 125–132dp, Issue #31 fresh add 191dp.
- API 31+ responsive `SizeF` maps are **not** used in 1A. They select by actual per-orientation size,
  which would put a portrait 4×1 (≈122dp tall on the moto g13) into Card and break the
  one-class-per-widget rule. They return when Square / Large need them (Phase 3), with fresh
  measurements.

## 4. Resolver

`DesignResolver.resolve(design, SizeContext, RenderEnvironment) → ResolvedDigitalSpec` is pure Kotlin:

1. Size class from `SizeContext`, then apply `overrides[sizeClass]` (`null` = inherit).
2. Typography: font id → `FontCatalog` face selection (requested → effective weight through
   `RemoteViewsFontWeightPolicy`; legacy families are exact only at 400, as in PR #27).
3. Color: `rgb` + `opacity` → effective ARGB.
4. Alignment START/CENTER/END → relative gravity (RTL handled by the host's layout direction).
5. X/Y: X is mirrored in RTL. Effective 0dp before API 31; clamped to ±50% of the target size
   (contract §3).
6. Date pattern: `formatPattern ?: environment.localeAutoDatePattern`.
7. Background: NONE / SOLID, effective radius (API 31+ exact or system, below that the nearest
   drawable variant).
8. Every requested ≠ effective difference is recorded as a `Degradation` for editor disclosure.

## 5. Composer and provider

- Template `clocky_digital_template.xml`: background `ImageView` + vertical content `LinearLayout` with
  two slot `LinearLayout`s (time, date). A slot's gravity carries the element's alignment
  (`LinearLayout.setGravity` is remotable).
- **Font fragments** (End-State §12 decision 1): one layout per font, added to a slot with
  `RemoteViews.addView`. `clocky_font_system_sans.xml` holds 9 weight faces (`textFontWeight` on
  API 28+, family aliases below that). Each MVP family has a single-face fragment. Per-element
  properties (size, color, spacing, format, translation) are set on the child `RemoteViews`, so
  time and date can share fragment layouts.
- Fragments never use theme attributes: measurement inflates them with the app context, and the
  host inflates them with the launcher's.
- Every slot gets `removeAllViews` before `addView`. Launchers `reapply()` an update whose root
  layout matches the views on screen, so the `addView` actions run again on the live tree.
- When the date is hidden, the date fragment is not added at all.
- The time format is always explicit. Follow-system sets both `h:mm` and `HH:mm` and TextClock
  picks one; the AOSP `kk:mm` showed 24:05 after midnight. Forced modes set one format.
  `leadingZero` selects `hh` (contract §8).
- Fit: requested sizes first. If they don't fit, time and date scale down by one common factor
  (binary search, as in Phase 0). The measurement runs on the **production RemoteViews applied
  once per orientation**: the solver changes text sizes on the inflated views. It uses worst-case
  strings: the longest time in the active format, and the widest date across sample dates. The
  result therefore stays valid as the day changes; `DATE_CHANGED` is not delivered to manifest
  receivers on API 26+.
- Portrait and landscape: separate fits (portrait minW × maxH, landscape maxW × minH), each built
  fully before `RemoteViews(landscape, portrait)`. This applies on every API level.
- Provider: `onUpdate` / `onAppWidgetOptionsChanged` / `onDeleted` / `LOCALE_CHANGED` (Locale Auto
  date pattern) / `TIME_SET` (sent when the user toggles 12/24h; re-fits the time). Tap-to-open
  Clocky is kept, gated by `Utils.isWidgetClickable` (not on the keyguard). After saving, the config
  Activity updates through a shared coordinator (`AppWidgetManager.updateAppWidget`) instead of an
  explicit broadcast to a provider class. TextClock ticks time and date, so there is no app update loop. The world-city
  list, next-alarm row, day-change alarm and AOSP widget-count analytics are not part of the Clocky
  Digital path. The next alarm returns as the Info line (Phase 2) and cities as the World Clock
  family (Phase 4).
- `android.appwidget.oldName` moves placed widgets to the new ComponentName with their ids, so their
  settings migrate in place; this needs device verification. Settings are deleted only in `onDeleted`.
  There is no list-diff pruning, because a transiently empty `getAppWidgetIds` would wipe everything.

## 5a. Delivery units

1. Model v2 + codec/migration + resolver (pure; Robolectric only for org.json).
2. Composer + Clocky provider + store switch + manifest switch (`oldName`), font-fragment spike on
   API 25 / 30 / 34 (device) / 35 hosts. The End-State asks for 23/28/31/35; these hosts cover the
   same code branches (pre-28 aliases, 28+ `textFontWeight`, pre-31 / 31+ translation and outline).
3. Removal of the unreachable AOSP Digital provider, city service, sizer and old renderers.
4. PreviewHost in the existing config screen.
5. Config controls for font / color / opacity / alignment / date format / background.

## 6. PreviewHost

`PreviewHost` takes the editor's draft design, a preview size class with its size in dp, and the
same resolver and composer, then `apply()`s the resulting RemoteViews into the config screen. The
widget and the preview differ only in input size, so they cannot drift structurally.

## 7. Open questions recorded for the owner

1. Per-size-class **weight** overrides: keep them (v1 compatibility, as implemented), or treat
   weight as typeface and remove them, as End-State §9's literal reading suggests?
2. End-State §5.2 says the date-format presets "go through the locale skeleton", while contract §6
   and the legacy importer treat patterns as literal. Read as skeletons, two of the six presets
   (`EEE, MMM d` and `EEE d MMM`) collapse into the same output. Phase 1A renders a non-null
   `formatPattern` **literally** and offers Locale Auto plus the six preset patterns. If presets
   become skeletons later, a deterministic migration maps these six known strings to preset ids.
4. Text sizes are set in px on the RemoteViews (as in Phase 0), so a system font-scale change is
   picked up only at the next widget update. This is relevant to the End-State §14 font-scale-200%
   check.
3. Below API 31, "Match system" corner radius falls back to 16dp (the framework default for
   `system_app_widget_background_radius`).
