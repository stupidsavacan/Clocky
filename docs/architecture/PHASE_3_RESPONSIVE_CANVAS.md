# Phase 3 — Responsive Canvas & Library: design record

> Status: **design only; nothing here is implemented yet.** The owner delegated the open semantics to this record on
> 2026-10-07; the rulings are in section 8 and are reflected in `CLOCKY_END_STATE.md` (section 9 lists the edits).
> Implementation can start with 3A-0. Values marked *provisional* still wait for measurement.
> Authority: `docs/product/CLOCKY_END_STATE.md` (§3, §5.8, §7, §8, §9, §10, §12, §13, §14). This record splits the
> End-State Phase 3 into four sub-phases. It builds on the Phase 1A and Phase 2 records
> (`PHASE_1A_DIGITAL_CORE.md`, `PHASE_2_STUDIO.md`).

End-State Phase 3 exit gate: **designs can be saved, duplicated and shared, and they work naturally at several sizes.**

## 0. Ground rules for every sub-phase

These come from End-State §3, §12 and the project rules. Every sub-phase PR repeats them as review checkboxes.

1. **Time is sacred.** Time is shown only by views that tick in the host (`TextClock`; later `AnalogClock` and
   `Chronometer`). No app-driven per-minute update, no bitmap clock, no resident Service. Phase 3 adds re-renders only
   for events: options/size change, save, restore, locale/time settings, next-alarm change, theme.
2. **Provider identity is frozen.** The registered component `com.android.alarmclock.DigitalAppWidgetProvider` is
   never renamed: renaming deleted placed widgets on an in-place update (Phase 1A §5, API 30 evidence). New families
   get **new** providers, and each new component name is frozen from its first release.
3. **Requested ≠ Effective.** The requested value is stored as typed. Degradation happens only in the resolver at
   draw time and is disclosed in the editor (`Degradation`). No sub-phase rewrites a stored request because a
   device or launcher cannot show it.
4. **Preview = the same RemoteViews.** `PreviewHost` applies the RemoteViews the provider sends. The canvas (3B) is an
   overlay on that applied tree, not a second renderer.
5. **Clocky owns presentation only.** AOSP domain code (`AlarmModel`, `CityModel`, `StopwatchModel`, `DataModel`,
   `alarms.db`) is not changed. For this reason backup covers Clocky presentation data only (ruling R8).
6. **No Google or Apple assets.** Picker preview images (3D) are rendered from Clocky's own widget.
7. **No silent deviation from END_STATE.** Where this record proposes a change to an End-State rule, it is listed in
   section 9 and was made in END_STATE before implementation.
8. **Device rules.** Device work uses `python tools/device/cdev.py` only. No uninstall, `pm clear` or `logcat -c`.
   "Verified" means actually executed on the named host; CI green is not device verification.
9. **Schema stays 2, additive only.** No sub-phase bumps `schema`. New keys are optional and their defaults reproduce
   the current output. Every built-in and a Phase 1A/1B/2 document must round-trip unchanged
   (decode → encode → decode is equal, and the resolved spec is equal).

## 1. Sub-phases, order and PR boundaries

```
1a AM/PM marker fix (Phase 2 follow-up, small PR; independent)
3A-0 measurement (diagnostic build, no product change)
  └─ 3A Responsive core ──┬─ 3B Canvas (UI only; separate PRs)
                          └─ 3C Library & Stacked ── 3D Platform integration
```

| Sub-phase | What | Depends on | PRs (each one ships alone) |
|---|---|---|---|
| **1a** | Phase 2 follow-up: AM/PM marker is Latin `AM`/`PM` in every locale (section 1a) | marker-font spike | one PR, before 3A |
| **3A-0** | Diagnostic build to measure host sizes on several launchers; spike of `Map<SizeF, RemoteViews>` | — | spike branch, not merged; results recorded in this file |
| **3A** | Square / Large size classes, general nullable Patch, API 31+ size map and API 23–30 options path, metadata | 3A-0 results, R1–R3 | 3A-1 model + resolver (pure); 3A-2 render path + metadata |
| **3B** | Canvas: select, drag, snap, pinch, 1dp nudge, pseudo-resize | 3A merged and gated | 3B-1 selection + drag + snap; 3B-2 pinch + pseudo-resize |
| **3C** | My Designs, duplicate, favorites, Import/Export (`.clocky`, `CLOCKY2:`), share, apply to other widgets, Stacked family | 3A (Square class) | 3C-1 Library store + My Designs; 3C-2 Import/Export/share; 3C-3 apply-to-others; 3C-4 Stacked provider |
| **3D** | Backup/restore with `onRestored` id remap, `requestPinAppWidget`, generated previews | 3C (My Designs must exist to be backed up / pinned) | 3D-1 backup; 3D-2 pin; 3D-3 previews |

End-State §13 forbids putting the architecture change (3A) and the large UI (3B) in one PR. That is enforced by the
dependency: 3B starts only after 3A is merged **and** has passed its shipping gate. 3B and 3C may run in parallel
after 3A because they touch different code (`widget/studio` canvas vs `design/library` + a new provider). When they
both touch `DesignEdits`, 3B lands first.

## 1a. Phase 2 follow-up: AM/PM marker shows 午前/午後 (first PR, before 3A)

**Problem.** The Phase 2 AM/PM suffix is a `TextClock` with the pattern `a` (`DesignResolver.resolveAmPm`,
`ResolvedAmPm.format12Hour`). `TextClock` formats with the **host's** locale, so on a ja-JP launcher the widget shows
`午前` / `午後`. The Japanese Studio labels say 午前・午後 as well (`values-ja/clocky_studio_ui_strings.xml`:
`clocky_studio_ampm*`). Owner decision (2026-10-07, END_STATE §5.1 amended): Latin `AM` / `PM` remains preferred.
The later owner ruling below permits a disclosed localized fallback when a real host cannot apply the dedicated font, including API 26+.

**Constraint.** `TextClock` has no locale setter, and the pattern letter `a` is always localized. Switching the text
from the app at 00:00 and 12:00 would be an app-driven clock update: an alarm can be deferred by Doze, so the marker
could be wrong for a while. Principle 1 rules that out, as it rules out a bitmap.

**Chosen approach (spike first).** A dedicated marker font: an OFL font derived from a bundled family (renamed, no
reserved font name, license kept in `third_party/fonts/`), with ligatures that turn the two-digit hour `00`–`11` into
the glyphs `AM` and `12`–`23` into `PM`. The marker view is a `TextClock` with format `HH` in both 12- and 24-hour
fields, so the host keeps ticking it and it flips exactly at noon and midnight. It is excluded from accessibility (the
time view already speaks the time), and the font is used only for this view.

Spike checks: ligatures apply in RemoteViews `TextClock` on API 26 / 28 / 31 / 34 / 35 (`res/font` needs API 26);
the switch at 11:59 → 12:00 and 23:59 → 00:00 with Clocky killed; fit still measures the marker correctly.

**Owner ruling / fallback (2026-10-07, before further production implementation).** API 23–25 and any API 26+
launcher / RemoteViews host where the dedicated font cannot be applied use the localized host-ticking pattern `a`.
The resolver handles host font capability explicitly; it must not equate API 26+ with support. The requested
`behavior.amPm` is preserved and Studio always discloses `AmPmLocalized` as Requested → Effective. Preview and
placed widget use the same effective path and host capability: a Latin preview with a raw-hour widget is forbidden.
Hosts where the font path is established may use Latin `AM` / `PM`. A future stable Latin approach can be
reconsidered, but this ruling does not authorize app-driven noon/midnight changes, AlarmManager / WorkManager
marker updates, bitmap markers, resident/foreground Services, or a preview-only renderer.

**Shipping criteria after this ruling.** Latin on supported hosts; localized `a` plus disclosed degradation on
unsupported hosts; Preview = placed widget; host-side ticking; requested values retained. Universal Latin is no
longer a shipping gate.

**Observed blocker (2026-10-07).** moto g13 / API 34 / Motorola Launcher3 / ja-JP: Preview rendered `PM`,
but the placed widget rendered raw `18`. A debug-only cross-package RemoteViews probe showed Preview
`restricted=false`, marker Typeface applied; launcher resource Context `restricted=true`, marker Typeface absent.
Android 14 RemoteViews creates a restricted resource Context for another package, and TextView skips font resource
loading there. This is font non-application, not a GSUB failure. Same-package Robolectric apply tests did not cover
this boundary. This evidence led to the owner ruling above; a raw-hour marker must never ship. The probe was
removed, the original widget preferences were restored, and the cdev sessions were finished.

**Studio strings.** The ja labels change from 午前・午後 to `AM/PM` (for example `AM/PMのサイズ`), so the editor
uses the same word as the widget. This is a string change only.

**Compatibility.** No model change: `behavior.amPm` keeps its keys. Designs that had the suffix on show Latin markers on capable hosts and localized markers on incapable hosts.
This is a presentation decision at resolve time, with no migration or schema change.

**Tests.** Pure: resolver chooses the marker face vs the localized fallback per SDK and records the degradation.
Robolectric: the marker fragment carries `HH` and the marker font; ja strings. Device (cdev): moto g13 in ja-JP with a
12-hour widget shows the same effective marker as Preview (the observed API 34 host uses disclosed fallback);
API 25 emulator shows the disclosed fallback. The noon/midnight flip is observed
only if a session spans it (the device clock is not changed: that needs settings outside cdev's allowlist).

---

## 2. 3A — Responsive core

### 2.1 Purpose

Replace the two-class (Strip / Card) system with the four End-State classes, make per-class overrides general,
and let API 31+ launchers switch layouts during resize without calling the app.

### 2.2 What exists today (checked in the code, 2026-10-07)

Phase 2 removed the `SizeClassPatches` helper and the Phase 1A editor classes. What remains of the override system:

| Piece | Where | State |
|---|---|---|
| `enum SizeClass { STRIP, CARD }` | `design/model/DigitalDesign.kt` | two classes |
| `SizeClassResolver.resolve(minHeightDp)` | `design/model/SizeClassResolver.kt` | height only, `0 < h < 100` → Strip, else Card (incl. missing options) |
| typed `LayoutPatch` (10 nullable fields: `time/date.weight`, `time/date.sizeSp`, `time/date.xDp/yDp`, `date.visible`, `layout.template`) | `DigitalDesign.kt` | weight is the Phase 1A migration exception (Phase 1A §7 Q1; ruled by R3: kept for Strip/Card) |
| JSON: `layout.overrides.{strip,card}` as **path → value** (`PATH_*` constants) | `design/storage/DigitalDesignCodec.kt` | already the End-State §9 storage shape |
| decoding of unknown classes / unknown paths / unknown template names | same | **dropped silently** (only `SizeClass.entries` and known `PATH_*` are read; an unknown template becomes `null` in a patch or the default in the base) |
| `EditScope(sizeClass)`, `OverrideField` (8 fields), `isOverridden`, `revertOverride`, `patched` | `studio/DesignEdits.kt` | the Studio "Only for Strip/Card" scope writes here |
| built-ins: base template = Card template, `strip` patch = Strip template | `design/library/BuiltinDesigns.kt` | Clocky Default: Center Stack (Card), Inline (Strip) |
| Quick Tune: scales `sizeSp` inside every patch | `design/library/QuickTune.kt` | |
| resolver: one class per build, from `minHeightDp`; both orientations use it | `DesignResolver.resolve`, `DigitalWidgetUpdater.build` | this is the Phase 1A "one class per widget" rule |
| render: `RemoteViews(landscape, portrait)` on **every** API level; portrait = minW × maxH, landscape = maxW × minH | `DigitalWidgetUpdater` | no `SizeF` map yet |
| metadata: min 250×70dp, minResize 250×40dp, targetCell 4×2 (v28 file), `reconfigurable`, `previewLayout` (API 31+), AOSP `previewImage` | `res/xml/digital_appwidget.xml`, `res/xml-v28/` | 250dp minimum width makes 2×N and 3×N unreachable |

So the Patch is already a path → value map on disk and a typed record in Kotlin. 3A extends both; it does not
introduce a second override mechanism.

### 2.3 Changes

**(a) Size classes.** `SizeClass` gains `SQUARE` and `LARGE` (additive; JSON keys `square`, `large`). The resolver
gets a `SizeClassRule` object with the thresholds as named constants:

| Constant | End-State §9 value | Status |
|---|---|---|
| `STRIP_MAX_H` | 100 dp | **provisional until 3A-0 measurement** |
| `LARGE_MIN_H` | 200 dp | **provisional** |
| `SQUARE_RATIO` (w < ratio × h) | 1.4 | **provisional, and already in doubt** (see 2.4) |

Ruling R1: one class per widget, used for both orientations. Which width and height feed the rule is fixed from the
3A-0 data. Until then the code keeps the Phase 1A input (`minHeightDp`) for Strip and does not enable Square/Large.

**(b) General Patch.** Specification proposal (End-State §9):

```json
"layout": {
  "template": "CENTER_STACK",
  "overrides": {
    "strip":  { "layout.template": "INLINE", "date.visible": false },
    "square": { "layout.template": "MINIMAL", "time.sizeSp": 72.0 },
    "large":  { "background.paddingDp": 24.0 }
  }
}
```

- Key = size class (lowercase name). Value = map from property path to value. `null` or absent = inherit.
- **Overridable (Layout only):** `layout.template`; `time|date|info.sizeSp`; `time|date|info.xDp`, `.yDp`;
  `date.visible`; `info.visible` (new, Card-only today); `time|date|info.alignment`; `background.paddingDp`;
  `date.gapDp` (End-State §5.2 "Time との間隔"; only if the model gains the base property in the same PR).
- **Not overridable:** font (`fontId`), colors and opacity, letter spacing, background type/colors/radius/border,
  shadow, behavior. A size change must not change the look's identity (End-State §9).
- **Weight (R3):** `time.weight` / `date.weight` stay readable and writable as Strip/Card overrides. They are the
  Phase 1A migration exception, and there is no lossless way to fold a per-class weight into the base. Square and
  Large do not offer weight overrides; the Studio scope switch hides weight there.
- Kotlin keeps a typed `LayoutPatch` (compile-time safety for the resolver). It gains the new fields plus
  `preserved: Map<String, String>` holding **unknown paths as raw JSON text**, and `DesignLayout` gains a preserved
  map for **unknown class keys**. Both are written back unchanged. This fixes the current silent drop, which would
  lose data when a document from a newer build (import, restore) is opened by an older one.
- Values are stored as requested; clamping (e.g. offsets to ±50 % of the class size, contract §3) happens in the
  resolver and is disclosed (`OffsetClamped`).
- Inheritance chain (R2): `square → card → base` and `large → card → base`; Strip and Card inherit from base. A
  widget that is Card today at 4×3 would otherwise lose its Card overrides on upgrade.
- `EditScope`, `OverrideField`, `isOverridden` and `revertOverride` are extended to the new fields and classes. The
  Studio scope switch becomes "Only for <class>" for all four classes. Studio's class selector shows four entries.

**(c) Render path.**

- **API 31+:** `RemoteViews(Map<SizeF, RemoteViews>)`. Keying is decided by the 3A-0 spike between
  **A** "the sizes the host reports in `OPTION_APPWIDGET_SIZES`" (each entry fitted to its real size; entry count
  usually 2) and **B** "A plus one anchor entry per other class" (so a live resize can cross a class boundary before
  the app is called). Recommendation before the spike: A, because B cannot express a ratio-based Square region with
  "largest entry that fits" selection and multiplies bitmap memory (2.5).
- **API 23–30:** unchanged mechanism: `onAppWidgetOptionsChanged` → `RemoteViews(landscape, portrait)`, each half
  fitted to its own size, the class taken from the R1 rule.
- The fit (`DigitalWidgetFit`) and composer are reused per entry. Nothing ticks: every entry carries TextClocks.

**(d) Metadata** (`res/xml/digital_appwidget.xml` and `xml-v28`): `targetCellWidth/Height` 4×2 (already in v28),
`minResizeWidth/Height` lowered toward 2×1 per End-State §9, `widgetFeatures="reconfigurable"` (already), no
`configuration_optional`. Lowering `minResizeWidth` from 250dp is gated on 3A-0: it exposes sizes (2×1, 3×1, 2×2)
that the current templates have never been fitted to. `minWidth` stays 250dp (the default placement).

### 2.4 Threshold measurement plan (3A-0)

The thresholds are **provisional** until this plan is completed. Known data already puts the ratio in doubt:

| Host | Size | Ratio w/h | End-State class | Intended |
|---|---|---|---|---|
| moto g13, Motorola Launcher3, 4×2 fresh add | portrait 363×260 dp | 1.396 | **Square** (< 1.4) | Card |
| same | landscape 667×132 dp | 5.05 | Card | Card |
| Issue #31 device, fresh add | 268×191 dp | 1.403 | Card by 0.003 | Card |

So with portrait sizes the default 4×2 on the reference device would become Square; with landscape sizes a 2×2 will
likely look wide. The rule cannot be fixed before measuring.

Procedure (Issue #31 style diagnostic build):

1. A **debug-only** source set logs, on every `onAppWidgetOptionsChanged` and `onUpdate`, one line per widget:
   `appWidgetId`, `MIN/MAX_WIDTH/HEIGHT`, `OPTION_APPWIDGET_SIZES` (API 31+), density, launcher package, and the
   class every candidate rule would produce. Tag `ClockySizeDiag`. Release builds contain none of it. This also fixes
   cdev finding U1 (`dumpsys appwidget` has no options line).
2. The debug metadata lowers `minResizeWidth/Height` to one cell so 2×1…5×4 can be reached. This lives in the debug
   source set only.
3. For each launcher, place and resize to: 2×1, 3×1, 4×1, 5×1, 2×2, 3×2, 4×2, 5×2, 3×3, 4×3, 5×3, 4×4 (as far as the
   grid allows), portrait and landscape. Collect with `cdev logs --since mark:<cell>` and `cdev collect`.
4. Hosts: Pixel Launcher on API 30 and 35 emulators; Motorola Launcher3 on the moto g13 (API 34). One UI Home, Nova
   and Lawnchair (End-State §9 matrix) are **only possible if the owner provides the device or installs the
   launcher**; Clocky does not download launcher APKs itself.
5. Decision rule: choose constants (and the input dimensions left open by R1) so that every cell in End-State §9's
   "representative cells" column lands in its intended class on every measured launcher, with at least
   8 dp / 0.1 ratio margin from a boundary. If no rule satisfies this, stop and propose an End-State §9 amendment
   with the data.
6. Record the raw table in this file (section 2.10) and the chosen constants with a test that pins every measured
   size to its class.

### 2.5 `Map<SizeF, RemoteViews>` estimate and spike

Framework limits to re-confirm in the spike (documented API behavior, not measured here):

- A size map has at most **16** entries; all entries must come from the same package.
- Total bitmap memory of one update ≤ **screen width × height × 4 bytes × 1.5**
  (moto g13 720×1600: ≈ 6.6 MiB; API 30 emulator 1080×2280: ≈ 14.1 MiB). Exceeding it throws.
- Binder transaction buffer ≈ 1 MiB per process, shared; large bitmaps travel as ashmem, the rest of the parcel
  does not.

Estimate for the worst current design (gradient or outline background, which is the only bitmap; text is TextClock):

| Entry | Size on moto g13 (px, density 1.75) | Bitmap ARGB_8888 |
|---|---|---|
| Card portrait 4×2 | 635×455 | ≈ 1.16 MB |
| Card landscape 4×2 | 1167×231 | ≈ 1.08 MB |
| Strip portrait 4×1 | 635×213 | ≈ 0.54 MB |
| Large 4×3 (estimate) | ≈ 635×680 | ≈ 1.73 MB |
| `RenderedBackground` cap | 1.2 MP | 4.8 MB |

- Keying A (host sizes, usually 2 entries): ≈ 2.2 MB on a 4×2 moto widget, same as today.
- Keying B (A + three anchors): ≈ 4.5–5 MB on the moto, close to the 6.6 MiB limit; two capped bitmaps alone
  (9.6 MB) already exceed it. That is a second reason to prefer A. If B is needed, the gradient can be drawn small
  and stretched (it scales without artifacts) while corners use `clipToOutline` on API 31+; outline bitmaps cannot.
- Non-bitmap parcel size per entry: one template plus 2–4 font fragments with their actions; expected well below
  the binder limit, to be measured.
- Generation cost: per entry one resolve (pure, cheap), one fit (inflates and measures the production RemoteViews,
  binary search), one compose, and possibly one bitmap draw. Today there are 2 entries; A keeps 2, B makes 5.
  Proposed budget (to be confirmed or replaced by the spike): p95 ≤ 100 ms per entry and ≤ 400 ms per update on the
  moto g13; above that, the provider moves the work to `goAsync()` (the broadcast ANR limit is far above this, but the
  main thread is shared with the editor).

Spike procedure (branch `spike/3a-size-map`, never merged):

1. Build the map with A and B behind a debug flag. Log per entry: key size, class, compose time (`nanoTime`),
   bitmap bytes, and parcel size (`writeToParcel` into a `Parcel`, `dataSize()`).
2. Worst-case design: gradient background, Info line, seconds, AM/PM, shadow.
3. Hosts: API 35 emulator, moto g13 (API 34). API 31–33: create an emulator for it (local AVD, no device risk); today none exists.
4. Checks: (i) during a resize drag the launcher switches entries without an app callback (no
   `onAppWidgetOptionsChanged` until the drop, visible change on screen); (ii) rotation picks the right entry;
   (iii) no `TransactionTooLargeException` / bitmap-memory `IllegalArgumentException` in `cdev logs`; (iv) after
   `am kill` of Clocky the TextClocks keep ticking; (v) preview = widget for each class.
5. Exit criterion: A or B chosen, measured numbers recorded in section 2.10, budgets met. If neither works on a host,
   that host keeps the API 23–30 path (options-changed) and the editor discloses it.

### 2.6 Not changed

Provider component name; store location (`clocky_widget_settings` / `widget.<id>.settings`); schema number; fonts,
colors, tokens, Info, tap zones; the AOSP Analog widget; the no-tick rule; Gallery and Quick Tune flows (they gain
class awareness only through the resolver).

### 2.7 Compatibility and migration

- Additive keys only (`square`, `large`, new patch paths, preserved maps). No migration step runs on load.
- With R2, no existing widget changes appearance: every size that is Card today keeps its Card
  patch through the chain, and Square/Large have no patches of their own yet.
- Built-ins keep their content and version. Square/Large templates for built-ins come as **new built-in versions**
  (`builtinId@version`); placed widgets keep their snapshot (End-State §8).
- Round-trip tests: all built-ins, a Phase 1A document, a Phase 1B document, a Phase 2 document, and a document with
  unknown class keys and unknown paths decode → encode → decode to an equal value and the same JSON keys.

### 2.8 Test strategy

- Pure Kotlin unit: `SizeClassRule` (every measured size from 2.10 pinned; boundaries ±1 dp), Patch inheritance
  chain, allowed/forbidden paths, preserved unknowns, clamping and `Degradation`, `DesignEdits` for all four classes.
- Robolectric (SDK 23/28/34): composer for each class and template; size map built on SDK 34 contains the expected
  keys and each entry applies; landscape/portrait pair on SDK 23/28; `PreviewHost` output equals the provider output
  for the same size; store round-trip.
- Device (cdev): section 2.9.

### 2.9 Device checks

Can be checked with cdev: classes on Pixel Launcher (API 30/35 emulators) and Motorola Launcher3 (moto g13) at
every reachable cell; resize across class boundaries; rotation; TextClock ticking after `am kill`; preview = widget;
no crash in the log; an existing Phase 2 widget unchanged after an in-place `cdev install`.

Cannot be checked now: One UI Home, Nova, Lawnchair (no host available); API 26–29 and 31–33 (no emulator yet; can
be created); 5-column cells on the moto's 4-column grid; font scale 200 % on a device (`cdev` refuses
`settings put`, by design).

### 2.10 Measurement results

*Empty until 3A-0 runs. Nothing in sections 2.3–2.5 that depends on these numbers is final.*

### 2.11 Shipping gate

- Thresholds and the R1 input dimensions are fixed from measured data, and END_STATE §9 is amended if they differ from 100 / 200 / 1.4.
- On every measured launcher, every intended cell lands in its intended class and renders without clipping or overlap.
- No existing widget changes appearance across the upgrade (moto g13 + one emulator, in-place install).
- Spike numbers are within budget; the bitmap-memory limit is never hit with the worst-case design.

### 2.12 Risks and exit conditions

| Risk | Exit / fallback |
|---|---|
| No threshold rule fits all launchers | Keep Strip/Card only, ship Square/Large later; propose an End-State §9 change with the table |
| Size map misbehaves on a launcher (blank, wrong entry) | Use the options-changed path on that host; disclose |
| Bitmap memory or parcel limits hit | Keying A only; stretch gradients; cap Large bitmap |
| Lowering `minResize` exposes sizes the templates cannot fit | Keep 250dp min width until those templates are fitted; Strip/Square at 2×N waits |

---

## 3. 3B — Canvas

### 3.1 Purpose

End-State §7 direct manipulation on top of the Phase 2 Studio: tap to select, drag with snapping, 1 dp nudge, pinch
to resize text, and a pseudo-resize handle. It adds input, not a renderer.

### 3.2 Changes

- **Hit testing on the applied tree.** `PreviewHost` already applies the production RemoteViews. The canvas finds
  the slot views (`clocky_time_slot`, `clocky_date_slot`, `clocky_info_slot`) in that tree and uses their bounds and
  `TextView.getBaseline()` for selection frames, guides and snap targets. No separate geometry model.
- **Edits through the existing API.** Drag → `DesignEdits.setOffset(d, target, x, y, scope)`; pinch →
  `DesignEdits.setSize(..., scope)`. Both go through `EditSession.apply(id, key)`, so one gesture is one undo step
  (the coalescing that Phase 2 already has for slider drags); `endGesture()` on finger up.
- **Snap:** widget center lines, padding edges, and the baseline / center of the other text elements. A snap gives
  haptic feedback (`HapticFeedbackConstants`), placing a second finger during a drag disables snapping
  (End-State §7). Snap thresholds in dp, constant.
- **1 dp nudge:** a D-pad in the Position panel; long press repeats. TalkBack users reach the same values through the
  existing numeric entry.
- **Pseudo-resize:** a handle on the canvas changes only the preview `SizeContext` between 2×1 and 5×4 (cell sizes
  from the measured launcher data of 2.10, nearest launcher by default). Crossing a class boundary switches the
  previewed class. With "Only for <class>" on, the edit scope follows the previewed class and the switch shows
  the class name (R5).
- **Offsets below API 31:** the preview cannot show translation there (it is the same RemoteViews), so dragging
  would move nothing on screen. Ruling R4: drag is disabled there and the canvas says why (position is not shown
  on this Android version); selection, pinch and numeric entry stay available. No ghost overlay.

### 3.3 Not changed

Model, schema, resolver, composer, provider. Studio panels and numeric entry stay as they are; the canvas is an
additional input. No multi-select, layers or grouping (End-State §7 rejects them).

### 3.4 Compatibility

No storage change. A design edited on the canvas is byte-for-byte what the same values typed into Studio produce.

### 3.5 Test strategy

- Pure unit: snap solver (inputs: element boxes, baselines, finger position → snapped offset), gesture → edit
  mapping, coalescing (one drag = one undo entry), clamping, RTL mirroring of X.
- Robolectric: `MotionEvent` sequences on the canvas (down/move/up, a second pointer, pinch with two pointers)
  produce the expected draft and undo stack; 48 dp targets; TalkBack actions exist on the canvas nodes.
- Device: 3.6.

### 3.6 Device checks

Can be checked with cdev: tap selection, single-finger drag (`cdev drag`, API 30+), preview = placed widget after
Save, undo after a drag, landscape Studio.

Cannot be checked with cdev: **pinch and the two-finger snap cancel** (cdev and `input` inject one pointer); haptic
feedback (not observable over adb); TalkBack (enabling it needs `settings put secure`, which cdev refuses); drag on
API < 30 (`cdev drag` is API 30+). These need the owner's hands on the device or an instrumented test; until then
they are reported as not verified.

### 3.7 Shipping gate

Drag, snap, nudge and pinch change only the requested offset/size in the current scope; every gesture is one undo
step; the preview is the production RemoteViews throughout; the placed widget matches after Save on the moto g13 and
an API 35 emulator; nothing regresses in the Phase 2 Studio tests.

### 3.8 Risks and exit conditions

| Risk | Exit / fallback |
|---|---|
| Re-applying RemoteViews per move frame is too slow (jank) | During the gesture move a lightweight overlay, apply RemoteViews on finger up only; if that still drifts from the result, ship drag without live preview and keep sliders |
| Slot bounds differ between preview and launcher (host padding) | Show the launcher padding hint (End-State §7) and rely on calibration (End-State §9.4, later) |
| Pinch conflicts with the scroll container | Canvas consumes two-pointer events; if unreliable, pinch is dropped and size stays slider-only |

---

## 4. 3C — Library & Stacked

### 4.1 Purpose

Make designs assets: My Designs, duplicate, rename, delete, favorites, Import/Export, share, "apply to other
widgets", and the Stacked family.

### 4.2 Changes

- **`DesignRepository`** (End-State §12): built-ins stay in code/assets, immutable and versioned
  (`builtinId@version`); user designs are `files/designs/<uuid>.json`, each holding a schema-2 design plus
  `{id, name, createdAt, updatedAt, favorite}`. Favorites of built-ins are a small list of built-in ids in the same
  directory. Widgets keep snapshots: deleting or editing a My Design never changes a placed widget (End-State §8).
- **Studio / Quick Tune:** "Save to My Designs" (copies the draft; a new id), "Duplicate", "Rename", "Delete"
  (confirmation), favorite star. Gallery gets the My Designs and Favorites tabs (End-State §6).
- **Export:** `.clocky` file = UTF-8 JSON
  `{"format": "clocky-design", "formatVersion": 1, "name": "...", "design": { schema-2 document }}` via
  `ACTION_CREATE_DOCUMENT` (no storage permission). **Text code (R7):** `CLOCKY2:` + base64url(deflate(UTF-8 JSON of the same envelope)), name
  included, 8 KB decoded limit (above it, share the file). No `VIEW` intent filter for `.clocky` in v1. Neither
  contains an `appWidgetId`, calibration or any device data.
- **Import** (file via `ACTION_OPEN_DOCUMENT`, or pasted text code): parse → reject unknown `formatVersion` / future
  `schema` with a message → decode with the same codec (unknown keys preserved) → clamp at draw time as usual →
  unknown font id → the same category's default with a disclosure (End-State §8) → save as a new My Design. Import
  never writes into a widget directly.
- **Share:** Android share sheet only, as text code or as a `.clocky` file through a `FileProvider`
  (`${applicationId}.files`; the existing `${applicationId}` authority belongs to the AOSP `ClockProvider`).
- **Apply to other widgets:** a list of placed Clocky widgets (live thumbnails from `PreviewHost`), multi-select,
  confirm, then write the snapshot into each and call the updater. Ruling R6: within
  a family the whole snapshot is copied; across families (Digital ↔ Stacked) only style (tokens and elements) is
  copied and the target keeps its template and overrides. Calibration is never copied.
- **Stacked family:** a new provider (component `com.stupidsavacan.clocky.widget.stacked.StackedWidgetProvider`,
  frozen on first release — R10), metadata default 2×2 /
  min 2×2, `reconfigurable`, its own `previewLayout`. It shares the Design model, resolver and composer: a `STACKED`
  template (additive enum value) with an hour row and a minute row as two TextClocks (`HH` or `h`/`hh`, and `mm`),
  so time still ticks in the host. Hour/minute colour and weight split ships in v1; line spacing comes later; Phase 3 has 4 Stacked
  built-ins, one per kit (R10).
  `WidgetInstance` records the family so the editor offers the right starting templates.

### 4.3 Not changed

Widget store location and per-id snapshots; the Digital provider; the no-server rule (no cloud, no community
gallery, no search — End-State §10); fonts stay bundled, so an export carries ids only.

### 4.4 Compatibility and migration

- Existing widgets need no migration. My Designs starts empty.
- A Phase 1A/1B/2 widget's design can be saved to My Designs and exported unchanged (round-trip test).
- The `STACKED` template is additive, but an older build drops unknown template names. Because downgrade installs are
  not allowed (`install -d` is refused) this matters only for files and restores (3D); the preserved-unknowns change
  of 3A covers patches, and the base template decode is extended the same way.

### 4.5 Test strategy

- Pure unit: repository (create/duplicate/rename/delete/favorite, id uniqueness), `.clocky` and `CLOCKY2:` encode →
  decode identity for every built-in and for stress designs, rejection of bad input (truncated, wrong prefix, future
  schema, oversized), unknown-font fallback, apply-to-others mapping.
- Robolectric: My Designs UI, SAF intents built correctly, share intent, apply-to-others updates every selected id
  and nothing else, Stacked composer on SDK 23/28/34, Stacked provider lifecycle (add, delete clears settings).
- Device: 4.6.

### 4.6 Device checks

Can be checked with cdev: save / duplicate / rename / delete / favorite; export to a file through the system picker
and import it back on the same device; text code copy and paste-import; apply to two placed widgets and see both
change; add a Stacked widget from the picker, resize it, reconfigure, delete; the Digital widget unchanged.

Cannot be checked with cdev: importing a file produced elsewhere (cdev never writes to `/sdcard`, and SAF cannot read
`/data/local/tmp`; the owner can place a file); actually sending a share to another app or person (an outward action,
not done); Stacked on launchers other than Pixel / Motorola.

### 4.7 Shipping gate

End-State Phase 3 gate for this part: a design can be saved, duplicated, exported, re-imported identically, shared
as text, and applied to other widgets; Stacked renders at 2×2 on the measured launchers with the time ticking in the
host; no placed widget changes unless the user applied a design to it.

### 4.8 Risks and exit conditions

| Risk | Exit / fallback |
|---|---|
| Text code too long for messaging apps | Compressed (R7); above the limit, offer the `.clocky` file instead of a code (the code never drops content) |
| Stacked hour/minute split doubles TextClocks and breaks fit | Ship Stacked without split styling; the split stays Power-user for Phase 5 |
| Stacked component name later regretted | Not reversible after release; fixed by R10 before 3C-4 |

---

## 5. 3D — Platform integration

### 5.1 Purpose

Backup and restore with appWidgetId remapping (End-State §8, §14 Essential), "Add to home" through
`requestPinAppWidget` (End-State §6), and picker previews (End-State §9).

### 5.2 Current state (checked, 2026-10-07)

- `AndroidManifest.xml`: `android:allowBackup="false"`, `backupAgent="DeskClockBackupAgent"`,
  `fullBackupContent="@xml/backup_scheme"`, `fullBackupOnly="true"`. The AOSP manifest has the same
  `allowBackup="false"`. **Clocky currently backs up nothing.**
- `res/xml/backup_scheme.xml` includes only AOSP data: `alarms.db` and `com.android.deskclock_preferences.xml`. Clocky's
  `clocky_widget_settings.xml` is not listed. There is no `dataExtractionRules` (API 31+).
- `DeskClockBackupAgent` (AOSP) reschedules alarms after a restore.
- The Digital provider does not override `onRestored`.
- `previewLayout` exists for API 31+; below 31 the picker shows the AOSP `previewImage`.

### 5.3 Changes

- **Backup (3D-1):** turn backup on with explicit include rules for Clocky presentation data:
  `clocky_widget_settings.xml` and `files/designs/`. `fullBackupContent` for API 23–30 and `dataExtractionRules`
  (cloud backup and device transfer) for API 31+. AOSP `alarms.db` / preferences stay **excluded** (R8) so the
  domain's behavior does not change in a presentation PR.
- **`onRestored(context, oldIds, newIds)`** in `ClockyDigitalWidgetProvider` and the Stacked provider:
  read every `widget.<old>.settings` first, then write all `widget.<new>.settings`, then remove old keys that are not
  also new ids (two-phase, so chains like 5→6, 6→7 cannot overwrite each other); idempotent if delivered twice; then
  update the new ids. No list-diff pruning (Phase 1A lesson). If the old key is missing (restore order is not
  guaranteed), the new id renders Clocky Default and the miss is logged.
- **Pin (3D-2):** "Add to home" in Library / Quick Tune calls `requestPinAppWidget` when
  `isRequestPinAppWidgetSupported()` (API 26+; otherwise the button explains how to add from the launcher). The chosen
  design is stored under a pending token; the success callback (`PendingIntent` with an explicit component; mutable
  because the system adds `EXTRA_APPWIDGET_ID`) writes it to the new id and updates. Until the callback arrives, the
  widget shows Clocky Default. Whether launchers start the configure Activity for a pinned widget is checked in the
  spike.
- **Previews (3D-3):** API 35+: `setWidgetPreview` with the Clocky Default RemoteViews from the same composer, called
  on first launch and after an app update, never periodically (the API is rate-limited; the result is checked).
  API 31–34: the existing `previewLayout`. Below 31: a PNG **rendered from Clocky's own widget** (Robolectric or an
  emulator capture of Clocky Default), replacing the AOSP image. No Google asset.

### 5.4 Not changed

AOSP backup agent logic; alarm scheduling; the store format; provider component names.

### 5.5 Compatibility

Backup content is the schema-2 documents as they are; a restore into an older build relies on the preserved-unknowns
decoding from 3A/3C. Ids are remapped, never reused blindly.

### 5.6 Test strategy

- Pure unit: remap function (identity, shift, swap, chain, overlap, duplicate delivery, missing old key).
- Robolectric: `ACTION_APPWIDGET_RESTORED` broadcast with `EXTRA_APPWIDGET_OLD_IDS` / `EXTRA_APPWIDGET_IDS` reaches
  `onRestored` and moves the stored designs; the backup XML parses and lists the intended files; the pin request is
  built with the right component and callback; `setWidgetPreview` is called once per version.
- Device: 5.7.

### 5.7 Backup / restore verification procedure and its limits

What can be done inside the cdev safety rules:

1. Static: the merged manifest (`aapt dump xmltree` / the build's merged manifest) shows `allowBackup="true"` and the
   rule files; the rules list exactly the intended paths.
2. On an **emulator only**: `cdev adb -- shell bmgr enabled`, `bmgr list transports`, then
   `bmgr backupnow com.stupidsavacan.clocky` with the local transport, and `dumpsys backup` to see that a backup of
   Clocky was taken. This writes backup data, not app data.
3. Robolectric tests for `onRestored` (5.6).

What cannot be done, and why:

- **`onRestored` on a real launcher.** The launcher only remaps widget ids when the system restores the launcher and
  widget state during device setup (new device, or setup after a factory reset). Factory reset is outside the rules,
  and there is no second device set up for transfer. `bmgr restore` restores app data only and does not trigger the
  remap.
- **`bmgr restore` of Clocky** overwrites `clocky_widget_settings.xml` with the backup, which destroys the placed
  widgets' current settings exactly like `pm clear` would. It is **not run on the moto g13 and not run on any
  emulator that holds widgets we care about**. Note: `bmgr` is currently **not** in the cdev raw-adb denylist. 3D-1
  should add `bmgr restore`, `bmgr wipe` and `bmgr clear` to the guard (developer tooling, its own small PR).

Therefore `onRestored` is reported as **"not verified on a device"** in the PR and the backlog until the owner runs a
real device-to-device restore outside cdev. Ruling R9: merging with this label is allowed; a release note may
claim restore support only after that manual restore passes. CI and Robolectric results are not described as device
verification.

### 5.8 Other device checks

Can be checked with cdev: pin request from Library on the API 35 emulator and the moto g13 (the launcher shows its
own confirmation; the owner or the test taps it), the placed widget shows the chosen design; picker preview on API 35
(generated), API 30 (PNG), moto g13 (API 34, so `previewLayout`).

Cannot be checked: pin on launchers that do not support it (none available); `setWidgetPreview` rate limiting over
hours (only the first call is observed); real restore (above).

### 5.9 Shipping gate

Backup includes exactly the intended files (static check + emulator `bmgr backupnow`); remap logic fully unit-tested
and disclosed as device-unverified until the owner's manual restore passes (R9); pin works on Pixel Launcher and Motorola
Launcher3 with the design applied; picker previews show Clocky's own rendering on all three API bands.

### 5.10 Risks and exit conditions

| Risk | Exit / fallback |
|---|---|
| Enabling backup changes AOSP domain behavior (alarms restored) | R8 keeps AOSP data excluded |
| Restore order: app data arrives after `onRestored` | Idempotent remap that is retried on the next `onUpdate` for ids without a design but with a pending remap record |
| Launcher never sends the pin callback | Widget stays Clocky Default; user reconfigures; disclosed in the pin sheet |
| `setWidgetPreview` refused (rate limit) | Keep `previewLayout`; retry on the next app update only |

---

## 6. Cross-cutting test matrix

| Layer | Tool | 3A | 3B | 3C | 3D |
|---|---|---|---|---|---|
| resolve / model / codec | JVM unit | classes, Patch chain, unknowns | snap solver, gesture mapping | repository, codes | remap |
| render / provider / UI | Robolectric SDK 23/28/34 | map + pair, preview = widget | MotionEvent sequences | Library UI, Stacked | restore broadcast, pin, previews |
| host behavior | cdev on device/emulator | thresholds, resize, rotation | drag, Save | import/export, apply, Stacked | backup taken, pin, picker |

API 35 is not covered by Robolectric because CI runs JDK 17 (Phase 0 note); the API 35 emulator covers it on device.

## 7. Items that cannot be verified on a device (summary)

1. `onRestored` appWidgetId remapping after a real launcher restore (needs device setup restore; factory reset and
   `bmgr restore` are outside the rules).
2. A real cloud or device-to-device restore of Clocky data.
3. One UI Home, Nova and Lawnchair (no host available unless the owner provides one) — this leaves the End-State §14
   launcher matrix open.
4. Pinch zoom and the two-finger snap cancel (cdev injects one pointer).
5. Snap haptics (not observable over adb).
6. TalkBack operation of the canvas (cdev refuses `settings put secure`).
7. Font scale 200 % on a device (cdev refuses `settings put`).
8. Canvas drag on API < 30 (cdev drag is API 30+).
9. Importing a `.clocky` file made on another device (cdev does not write to `/sdcard`).
10. Sending a share to another app or person (outward action).
11. `requestPinAppWidget` on a launcher without pin support; `setWidgetPreview` rate limiting over time.
12. API 26–29 and 31–33 until emulators for them are created.
13. The AM/PM flip at noon and midnight unless a session happens to span it (changing the device clock is outside
    cdev's settings allowlist).

## 8. Rulings (open semantics, decided 2026-10-07)

The owner delegated these decisions. Each follows from the stated principle, not from guessing at device
behavior; where data is still missing (R1's input dimensions) the ruling says so.

| # | Question | Options considered | Ruling |
|---|---|---|---|
| R1 | What input decides the size class? | (a) one class per widget from one orientation, as Phase 1A; (b) per orientation / per `SizeF` entry (End-State §9 literal; portrait and landscape can differ) | (a) for predictable overrides. The input dimensions and all three thresholds stay provisional until 3A-0, because known data already misclassifies 4×2 portrait on the moto |
| R2 | What do Square and Large inherit from when they have no own value? | (a) base only; (b) `card` patch, then base; (c) one-time copy of `card` into `square`/`large` | (b): no placed widget changes look, no migration, explicit overrides still possible |
| R3 | Per-class weight overrides (Phase 1A §7 Q1) | keep as exception / remove (End-State §9: typeface is not overridable) | Keep for Strip/Card (no lossless removal exists); Square/Large offer no weight override |
| R4 | Canvas drag on API 23–30, where offsets render as 0 dp | (a) drag disabled with a disclosure; (b) drag edits the value, preview shows nothing; (c) overlay ghost (not the real RemoteViews) | (a): keeps principle 7 (preview = widget) and principle 5 disclosure |
| R5 | During pseudo-resize, does the "Only for <class>" edit scope follow the previewed class? | follow / stay | Follow, and show the class name in the scope switch, so an edit always lands on what is shown |
| R6 | "Apply to other widgets" copies what? | (a) the whole design snapshot; (b) style only (tokens, elements), keeping each target's template and overrides; plus: across families (Digital ↔ Stacked)? | (a) within the same family; across families copy style only and keep the target's template and overrides; never copy calibration |
| R7 | `CLOCKY2:` text code format | plain base64url JSON / base64url(deflate(JSON)) / include the design name or not / length limit | `CLOCKY2:` + base64url(deflate(UTF-8 JSON)), name included, 8 KB decoded limit; no `VIEW` intent filter for `.clocky` in v1 |
| R8 | Backup scope (backup is off today, as in AOSP) | (a) Clocky presentation only; (b) also AOSP `alarms.db` + preferences; also: cloud backup, device transfer, or both | (a), both cloud and device transfer; AOSP data stays out until a domain decision |
| R9 | `onRestored` cannot be device-verified within cdev. Is shipping with unit/Robolectric coverage and a "device-unverified" label acceptable? | accept / owner runs a real device-to-device restore before release / block 3D-1 | Accept for merge, block the release note's "restore" claim until the owner's manual restore passes |
| R10 | Stacked family scope | component class name (frozen once released); number of Stacked built-ins in Phase 3 (End-State's 16+ is the Phase 4 gate); hour/minute split styling and line spacing in v1 or later | Name `com.stupidsavacan.clocky.widget.stacked.StackedWidgetProvider`; 4 built-ins (one per kit); split colour + weight in v1, line spacing later |

## 9. END_STATE amendments made with this record (2026-10-07)

- Header: the current-state source of truth is the backlog; README is an overview (inconsistency 2 below).
- §4 Stacked: component name, 4 built-ins in Phase 3, hour/minute split in v1 (R10).
- §5.1 AM/PM: the marker is always Latin `AM` / `PM`, host-ticking only, locale marker as a disclosed fallback
  (section 1a).
- §7: drag disabled below API 31 with disclosure (R4); edit scope follows pseudo-resize (R5).
- §8: apply-to-others semantics (R6); `CLOCKY2:` format (R7); backup scope (R8) and the restore-verification policy (R9).
- §9: one class per widget, thresholds provisional until measured (R1); inheritance chain (R2); weight exception and
  preserved unknowns (R3); size-map keying decided by the spike.

Still to amend after 3A-0: the measured thresholds and input dimensions in §9.

## 10. Inconsistencies found while reading (reported, not fixed here)

1. `README.md` status line and `実装予定表.md` predate Phase 1A–2 (known to the owner).
2. `CLOCKY_END_STATE.md` header said the current-state source of truth is "`README.md` and the backlog"; the owner's
   instruction is that the backlog and END_STATE are authoritative and README is stale. **Fixed in END_STATE (section 9).**
3. `docs/WEB_AGENT_HANDOFF.md` still describes PR #29 as the baseline, the settings schema as `1`, and X/Y editing
   UI as not implemented. Phase 1A moved to schema 2; Phase 2 has the offset editor.
4. `docs/IMPLEMENTATION_BACKLOG.md` F4 "remaining Web-safe candidates" (X/Y UI, date format, color/opacity,
   alignment, background, degradation disclosure) are unchecked, but Phase 1A/2 implemented them. F5 importer is
   still genuinely pending.
5. `tools/device/README.md` says Strip is "not reachable by resizing" on the moto g13, while the backlog's Phase 1A
   moto evidence reports the editor's host-derived class as **Strip** after a one-row resize. Both are explained by
   cdev estimating from the portrait host view (~122 dp) while the app reads `OPTION_APPWIDGET_MIN_HEIGHT` (landscape),
   but the README sentence reads as a fact about the app.
6. End-State §8 makes backup "必須" (Essential) while the manifest has `allowBackup="false"` and the backup rules list
   no Clocky file (section 5.2).
7. End-State §9 places 268×191 dp in Card; under the 1.4 rule it is Card by 0.003, and the moto 4×2 portrait size is
   Square (section 2.4).
8. Codec drops unknown size-class keys, patch paths and template names (section 2.2): not wrong today, but a data
   loss path once files and restores can come from newer builds.
9. Phase 2 §7 notes that only the Time-first `layout-v31` template has a `clipToOutline` variant; Square/Large
   templates in 3A should not repeat that gap.
