# Clocky Widget Customization Contract

> Status: **normative implementation contract**
>
> Source of product truth: `docs/product/CLOCKY_END_STATE.md`, especially §5.8.
> This document exists to make the previously blocked renderer/editor/migration semantics directly actionable in implementation work. If this file and the End-State diverge, update both in the same change and treat the End-State as authoritative.

## 1. Layout templates

The Text engine supports seven semantic templates:

| Template | Composition | Primary use |
|---|---|---|
| Center Stack | Date above time, centered | Clocky Default / Google-like |
| Time First | Time above date | General |
| Inline | Time and date on one row, date aligned to the time baseline | Strip / 4×1 |
| Split | Date at upper start, time at lower end; mirrored in RTL | Card / Editorial |
| Stacked | Hour and minute on separate rows, date attached | Square |
| Corner | Large time at lower start, date above | Large / poster-like |
| Minimal | Time only | Any size |

Templates define anchors. User X/Y values are offsets from those anchors, not absolute screen coordinates.

## 2. Alignment and RTL

- Public/editor alignment values are **START / CENTER / END**.
- Do not expose LEFT / RIGHT as absolute alignment choices.
- Legacy MVP alignment indices map losslessly:
  - `0 -> START`
  - `1 -> CENTER`
  - `2 -> END`
- START and END follow locale writing direction.
- Horizontal X offsets follow writing direction as well; their effective sign reverses in RTL.
- Split and other directional templates mirror their semantic start/end placement in RTL.

## 3. X/Y position contract

- Persist X/Y as relative dp offsets from the active template anchor.
- Do not round the requested persisted value merely to fit a host.
- Editor/requested range:
  - X: ±50% of the active size class width.
  - Y: ±50% of the active size class height.
- Clamp only when resolving/rendering an effective value for the current host/size class.
- API 31+ may apply RemoteViews translation.
- API 23–30 preserve the requested value but resolve the effective translation to **0dp**.
- When a requested value cannot be applied on the current SDK/launcher path, the editor must disclose the degradation rather than silently rewriting the stored request.

## 4. Size mode

Two modes are normative:

- **Fit** — default. Text is automatically sized to fit its semantic region; the user controls a scale/multiplier. Existing AOSP binary-search autosizing may be reused as an implementation technique.
- **Fixed** — Advanced. User specifies sp. If the result does not fit, resolution may reduce the effective size to avoid clipping.

Requested and effective size remain conceptually separate when degradation/clamping occurs.

## 5. Color and opacity

The editor and model treat color and opacity as separate concepts.

- User-facing color is RGB.
- On current saves, normalize the stored color alpha channel to **FF**.
- Store opacity separately as 0–100% (or the model's equivalent normalized representation).
- If legacy/imported data contains ARGB alpha, fold it into opacity:
  - `effectiveOpacity = separateOpacity × (legacyAlpha / 255)`
- After normalization, do not preserve a second hidden alpha channel that would be applied again.
- Dynamic/Auto color behavior from the End-State is resolved after this base normalization contract.

## 6. Date format override

- The current contract is `formatPattern: String?`.
- `null` means **Locale Auto** and preserves the provider's locale-derived default behavior.
- A non-null string is an explicit override.
- Legacy date-format values map to their proven patterns and therefore become non-null explicit overrides.
- The editor must provide a way to return to Locale Auto rather than forcing one fixed pattern as the permanent default.

## 7. Legacy MVP background mapping

Legacy `bg` values map exactly as follows:

| Legacy | Current mapping |
|---|---|
| transparent / 0 | Background None |
| dark / 1 | Solid `#111111`, opacity 90% |
| light / 2 | Solid `#FFFFFF`, opacity 90% |

Additional migration semantics:

- Base padding: **18dp**.
- Compact-size padding override: **10dp**.
- The compact padding difference becomes a size-class override, not a separate global mode.
- Legacy light background also brings its proven default text color **#111111** when importing the old configuration.
- Do not invent radius/fallback behavior that was not present in the proven legacy source; current design defaults may apply only where migration does not claim to reproduce a legacy value.

## 8. Leading zero

- Current End-State behavior:
  - 12h mode may switch between `h` and `hh`.
  - 24h remains `HH`.
  - Default is **off** for 12h leading zero.
- The old MVP persisted **no `leadingZero` key**.
- The primitive importer must not synthesize a legacy leading-zero value; leave the current/default value untouched.

## 9. Primitive-key importer status

The legacy source contract is decoded and all persisted legacy semantics now have an explicit current mapping. Therefore the importer is **contract-unblocked**.

Implementation rules remain:

1. Existing consolidated JSON in `clocky_widget_settings` / `widget.<appWidgetId>.settings` wins.
2. Read only proven legacy keys/types/mappings.
3. Normalize into the current model using the contracts above.
4. Save through the current settings store.
5. Only after successful import, remove or permanently ignore the old per-widget primitive keys.
6. Never dual-write current values into the legacy primitive key space.
7. Do not invent profile data or a legacy leading-zero value.

Contract-unblocked does **not** mean implemented or device-verified. Importer code, regression tests, launcher/device verification, and any required schema migration remain separate work items.

## 10. Implementation boundary

This contract removes the former “semantics undefined” blocker for:

- X/Y editor controls.
- Date-format renderer/editor.
- Color/opacity renderer/editor.
- Alignment renderer/editor.
- Background renderer/editor and old-MVP migration.
- One-way primitive-key importer.
- Current leading-zero UI/behavior.

It does not by itself prove Android/launcher behavior. Device-dependent clipping, RemoteViews translation behavior, resize transitions, theme rendering, and launcher restore/rebind still require the verification gates in the End-State roadmap.

## 11. Current Digital 4×1 / 4×2 geometry (Phase 0)

This pins the geometry of the current Digital provider until the End-State size classes (§9 of `CLOCKY_END_STATE.md`, Phase 1A onward) replace it. It does not change the End-State target.

Provider metadata (`res/xml/digital_appwidget.xml`, `res/xml-v28/digital_appwidget.xml`) is restored from the retained MVP metadata in `ci/Clocky_MVP_source.zip` (`res/xml/clock_widget_info.xml`) rather than the AOSP generic values (`minWidth 206dp`, `minHeight 129dp`, `minResize 136×59dp`):

| Attribute | Value | API |
|---|---|---|
| `minWidth` / `minHeight` | 250dp / 70dp | all |
| `minResizeWidth` / `minResizeHeight` | 250dp / 70dp | all |
| `targetCellWidth` / `targetCellHeight` | 4 / 2 | 31+ (ignored below) |
| `widgetFeatures` | `reconfigurable` | 28+ |
| `configure` | `DigitalWidgetConfigActivity` (no `configuration_optional`) | all |

Profile resolution is unchanged from the merged implementation and is now pinned by tests:

- Input is the host `OPTION_APPWIDGET_MIN_HEIGHT` in dp, so one widget keeps one profile in both orientations.
- `0 < height ≤ 94dp` → 4×1 profile; anything else, including missing/zero options and the Issue #31 fresh-add value of 191dp, → 4×2 profile.
- The 94dp boundary is kept as is because existing per-profile overrides were saved against it. The old MVP used a different input (`OPTION_APPWIDGET_MAX_HEIGHT < 180dp` meant compact) and had no per-profile persistence, so that rule is not adopted here.
- The End-State §9 relaxation of `minResize` to a 2×1 equivalent belongs to the size-class work, not to this pin.

How a launcher converts these dp values into cells, and the fresh-add → configure → render → resize path, are launcher behavior and need device verification.
