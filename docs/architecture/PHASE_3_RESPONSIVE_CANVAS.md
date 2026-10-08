# Phase 3 — Responsive Canvas & Library: design record

> Status: **3A-0 complete (#47 merged); 3A-1 merged (#48); 3A-2 implemented, moto verified/restored; API35 p95 and alarm boot-crash gates failed. Keep Draft.** The independent Phase 2 follow-ups (#44–#46) are merged. The owner delegated the open semantics to this record on
> 2026-10-07; the rulings are in section 8 and are reflected in `CLOCKY_END_STATE.md` (section 9 lists the edits).
> Universal Latin AM/PM is temporarily paused and is not a Phase 3 gate. Before 3A-0, the Phase 2 font-host parity follow-up (1b below) must make Preview/fit use the same effective font as the placed widget. 3A-0 results and remaining production gates are recorded in §2.10 (2026-10-08).
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
1a AM/PM host-aware marker/fallback (Phase 2 follow-up; PR #44)
1b Bundled-font host parity (Phase 2 follow-up; separate PR)
3A-0 measurement (diagnostic build, no product change)
  └─ 3A Responsive core ──┬─ 3B Canvas (UI only; separate PRs)
                          └─ 3C Library & Stacked ── 3D Platform integration
```

| Sub-phase | What | Depends on | PRs (each one ships alone) |
|---|---|---|---|
| **1a** | Phase 2 follow-up: host-aware AM/PM path; localized `a` is accepted on incapable hosts and universal Latin is temporarily paused (section 1a) | marker-font spike / host capability | PR #44, before 1b |
| **1b** | Phase 2 follow-up: ordinary bundled-font host capability, explicit degradation, Preview/fit/placed-widget parity | PR #44 font audit / owner ruling | one product PR, before 3A-0 |
| **3A-0** | Diagnostic build to measure host sizes on several launchers; spike of `Map<SizeF, RemoteViews>` | 1b merged | spike branch, not merged; results recorded in this file |
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

**Owner pause ruling (2026-10-07, after the real-host audit).** Universal Latin AM/PM is temporarily not pursued.
PR #44 keeps the host-capability probe, safe localized fallback, accessibility work and the experimental Latin path
because they are useful infrastructure and preserve a future option, but Phase 3 does not wait for a launcher that
can apply the marker font. On ordinary unsupported hosts, localized `a` is the expected effective result.

**Shipping criteria after this ruling.** Localized `a` is acceptable on unsupported hosts; a proven-capable host may
use the retained Latin path. Preview = placed widget, host-side ticking and requested-value preservation remain
mandatory. Universal Latin is not a shipping gate.

**Observed blocker (2026-10-07).** moto g13 / API 34 / Motorola Launcher3 / ja-JP: Preview rendered `PM`,
but the placed widget rendered raw `18`. A debug-only cross-package RemoteViews probe showed Preview
`restricted=false`, marker Typeface applied; launcher resource Context `restricted=true`, marker Typeface absent.
Android 14 RemoteViews creates a restricted resource Context for another package, and TextView skips font resource
loading there. This is font non-application, not a GSUB failure. Same-package Robolectric apply tests did not cover
this boundary. This evidence led to the owner ruling above; a raw-hour marker must never ship. The probe was
removed, the original widget preferences were restored, and the cdev sessions were finished.

**Studio strings.** PR #44 keeps the neutral `AM/PM` control wording while the degradation notice explicitly tells
ja-JP users that the effective host marker is the locale form（午前/午後）. This avoids changing the stored semantics
again while universal Latin is paused.

**Compatibility.** No model change: `behavior.amPm` keeps its keys. Designs that had the suffix on show Latin markers on capable hosts and localized markers on incapable hosts.
This is a presentation decision at resolve time, with no migration or schema change.

**Tests.** Pure: resolver chooses the marker face vs the localized fallback per SDK and records the degradation.
Robolectric: the marker fragment carries `HH` and the marker font; ja strings. Device (cdev): moto g13 in ja-JP with a
12-hour widget shows the same effective marker as Preview (the observed API 34 host uses disclosed fallback);
API 25 emulator shows the disclosed fallback. The noon/midnight flip is observed
only if a session spans it (the device clock is not changed: that needs settings outside cdev's allowlist).


**Implementation result (2026-10-07, independent follow-up PR #44; ready for owner merge).**
- `RenderEnvironment.supportsLatinAmPmMarker` is an explicit runtime-only fact. API < 26, unknown HOME,
  system resolver, probe failure, or an unapplied/unshaped marker face resolves to localized `a` and
  `AmPmLocalized`. No requested values or capability flags are written to the design.
- `AmPmHostCapability` resolves the HOME launcher (scoped manifest package-visibility query), then applies
  the production marker RemoteViews in that launcher's package Context. It checks the dedicated Typeface
  and all 24 hour advances against the two substituted groups. The `android` package is deliberately
  excluded: its special Context falsely advertised support in the first capability attempt. Both provider
  and Preview use the same probe. This is a framework/launcher-context capability check, not a launcher allowlist.
- On a supported path the 3,132-byte OFL-derived, renamed Clocky AM PM Marker font has required `rlig`
  substitutions and Unicode decimal-digit aliases. It stays outside FontCatalog. Marker-only layouts carry
  accessibility exclusion in XML because the setter is not RemoteViews-remotable. Localized markers inherit
  platform faces; an inherited bundled face falls back to system sans for the marker alone, with disclosure.
  The requested time face is kept. Fit measures the same effective fragment used for display.
- Font regeneration (fontTools 4.53.1) reproduced the same SHA-256. No spike Activity, debug manifest,
  temporary resources, app clock updater, schema change, or provider/domain change is in the final tree.

**Tests.** `testDebugUnitTest`: 366 tests, 0 failures/errors/skips; `lintDebug`: successful; `assembleDebug`:
successful. Each production commit was followed by successful full unit/lint checks before subsequent
production work. cdev pure tests: 99 passed. Pure tests cover SDK × supported/unsupported host, unchanged
requests, hidden/24-hour cases, and platform marker fallback. RemoteViews apply/reapply, marker accessibility,
font/shadow variants, fit, disclosure and Preview/provider parity run at SDK 23/25/26/28/31/34/35.
Native raster/advance tests verify all 24 Latin and Arabic-digit hours plus tracking at SDK 26/28/31/34/35;
restricted Context and unknown/system HOME regression tests cover the false-positive capability boundary.
Existing built-in 8 and Phase 1A/1B/2 codec/regression tests pass.

**Device evidence (cdev, final production APK).**
- moto g13 / API 34 / Motorola Launcher3 / ja-JP: in-place install retained widget 23 and its original
  appearance before editing. Force 12-hour + suffix displayed `午後` in both Studio Preview and placed widget;
  Studio displayed `AM/PM → ロケールの表記` and explained the font fallback and retained choice. Screenshots
  showed no clipping/overlap. With Clocky absent (`pidof`), the host clock advanced `7:19 → 7:20`, retaining
  `午後`. Saved requests remained Force 12 / Suffix during fallback. Original preferences were then restored
  byte-for-byte; session finished. Local evidence: `s-20261007-190630-ZY22GSDPFW`, `0018-inspect`,
  `0020-inspect`, `0021-inspect`/`0023-inspect`, `0022-collect-fallback-post-kill`, `0033-collect-fallback-restored`.
- clocky-api25 emulator / API 25 / Pixel (Nexus) Launcher / en-US: in-place install retained widget 4.
  Localized `a` displayed `AM` in both Preview and placed widget, with the fallback notice in Studio and no
  observed clipping/overlap. `am kill` did not remove the process on this host; a cdev-wrapped `run-as` kill
  of the observed Clocky PID did. With Clocky absent before and after, the host clock advanced `10:27 → 10:29`.
  Original JSON values were all restored; Save added only existing optional default keys. Session finished.
  Local evidence: `s-20261007-192412-emulator-5554`, `0011-inspect`, `0013-inspect`, `0017-inspect`/`0018-inspect`,
  `0019-collect-api25-ticking-proof`, `0027-collect-api25-restored`.
- Collected crash buffers were empty and no crash/ANR was reported. The moto log buffer rolled over during
  the long session; API 25's Clocky-filtered log summary had zero relevant lines, so these logs are not
  comprehensive runtime-error evidence. Display and ticking claims above come from direct observations.

**Not exercised.** Natural 11:59 → 12:00 or 23:59 → 00:00 transitions; a real launcher positively supporting
this dedicated font; physical/API 26/28/31/35 launchers; One UI/Nova/Lawnchair; TalkBack spoken output;
non-HOME widget hosts; device RTL/large font scale; launcher landscape/resize during this follow-up.
Native shaping and same-package probe success are not described as real-launcher Latin verification.
The current capability represents the resolved HOME launcher; other host implementations need separate evidence.

**Historical shipping result (2026-10-07).** The amended marker gate was satisfied on the two
exercised hosts with the limitations above. The independent follow-ups are now merged (#43–#45);
3A-0 completed on 2026-10-08 (§2.10). Phase 3A production implementation has not started.

**Additional Phase 2 bundled-font audit (2026-10-07, owner requested; unresolved).** The same remote-font
restriction also affects ordinary Time/Date bundled fonts. A temporary debug-only Activity published seven
rows directly into the existing widget on Motorola Launcher3 / moto g13 / API 34 / ja-JP. Each row used the
existing production `FontFragmentTable` fragment, weight 400, shadow OFF, a 24px TextClock, and literal
format `'0123456789 AMPM'` in both hour modes. The exact same RemoteViews was applied for same-package
Preview. This fixed audit sample is not a replacement production clock.

| Requested face | Same-package Preview width (px) | Placed widget width (px) |
|---|---:|---:|
| system sans | 209 | 209 |
| Poppins | 217 | 209 |
| Bebas Neue | 149 | 209 |
| DM Serif Display | 195 | 209 |
| Barlow Condensed | 149 | 209 |
| IBM Plex Mono | 210 | 209 |
| Varela Round | 236 | 209 |

Placed widths come from TextClock UI bounds, not a simulated host Context. All six bundled rows' 209×28px
glyph crops were byte-identical to the placed system-sans crop; the screenshot was also visually inspected.
The corresponding launcher-package Context probe was restricted and measured 209px for every face;
same-package Contexts were unrestricted and had distinct Typefaces/widths. The CTS
[FontResourceTest](https://android.googlesource.com/platform/cts/+/2d7144b53f96b0eebb0f18130dc2cc64aeb97c3f/tests/tests/text/src/android/text/cts/FontResourceTest.java)
independently expects RemoteViews to ignore custom font files.

This confirms an existing Phase 2 defect on this host: a bundled request can remain undisclosed while the
widget renders a system face and Preview/fit use the bundled face. Potential fit/clipping consequences were
not tested. Any earlier same-package/visual-only bundled-font evidence is insufficient to claim real-host
support. Ordinary bundled-font capability resolution/disclosure is **not fixed by PR #44**; the marker-only
shipping result above does not establish full font parity for arbitrary designs. No new font fallback
semantics or ordinary-font production changes were introduced during this audit.

Local cdev evidence: `s-20261007-195819-ZY22GSDPFW`, `0003-inspect` (same-package Preview), `0006-logs`
(Context/width probe), `0008-inspect` and `0009-collect-bundled-font-placed-proof` (actual launcher rows),
`0012-collect-bundled-font-audit-restored` (normal widget restored). Saved preferences were byte-identical
before/after; the audit did not change the design or device clock. The probe was removed, the clean APK
rebuilt successfully and installed in place, and the session finished. Its 1,367 runtime/resource entry
digests matched the earlier device-tested production APK; the whole archive hash differed after rebuilding.
No audit code/resources ship. Not exercised: other real hosts/APIs, other weights/shadows, live production
bundled designs across size/fit boundaries. The ordinary-font issue needs separate follow-up before claiming
general Preview/widget font parity.

---

## 1b. Phase 2 follow-up: bundled-font host parity (required before 3A-0)

PR #44's real-launcher audit found that the six Phase 2 bundled weight-400 faces (Poppins, Bebas Neue,
DM Serif Display, Barlow Condensed, IBM Plex Mono and Varela Round) all render as the same system-sans glyphs
on moto g13 / API 34 / Motorola Launcher3, while same-package Preview renders the requested faces. Width and
pixel-glyph comparisons confirmed the placed fallback; this supersedes the earlier visual-only Poppins claim.

**Owner ruling (2026-10-07).** Keep the requested font ids and the long-term typography library, but never assume
that an APK-bundled font is usable in cross-package RemoteViews merely because the SDK is API 26+. Generalize the
host-capability approach proven in PR #44: resolve bundled-font support at render time, fall back to a platform-safe
system face on unsupported/unknown hosts, disclose the degradation, and make Preview and fit consume that same
effective face. Silent placed-widget fallback is forbidden. This follow-up is a Phase 2 correctness repair, not a
Phase 3 feature, and must merge before 3A-0 measurements so size/fit data is not collected against a Preview font
that the real widget cannot render.

The follow-up may additionally inventory platform/system families on API 34 hosts, but it must not block on reaching
the long-term 24-family count. The immediate gate is truthful requested→effective resolution and Preview/fit parity.

### 1b.1 Implementation record (2026-10-07, Phase 2 follow-up 1b PR)

**Model.** `RequestedFont + RenderEnvironment -> FontCatalog.resolve -> EffectiveFace + Degradation?` is a pure
transformation. `RenderEnvironment.supportsBundledFonts` (default `false` = unknown host) comes from
`HostFontCapability.probe(context)` (widget/digital), the generalization of PR #44's `AmPmHostCapability`: it resolves the
HOME launcher package (never `android`, never Clocky), creates that package's resource Context and applies a real bundled
fragment (`clocky_face_poppins_off`) with it, accepting only if the shaped width matches the bundled face **and** differs
from the platform default. The AM/PM marker probe now lives in the same object and is gated on it (the marker is itself a
bundled font). Nothing is persisted; the result is recomputed at every resolve, so a launcher change is never judged by a
stale answer (no runtime cache: the probe is one resolve + one inflate).

**Granularity: one flag, not per face.** All bundled faces share one mechanism (a `res/font` reference on a TextView inside
a RemoteViews tree applied with the host Context). The PR #44 audit showed all six families failing together, and no
per-face difference was seen (the 1b probe itself only tests Poppins 400; the six families were then each selected in Studio on the moto and all rendered as system sans in Preview and placed widget). A per-face probe would add cost with no evidence of face-level variation.
If a future host is found that renders only some faces, the flag can be widened to a per-resource set behind the same
`RenderEnvironment` seam.

**Requested vs effective.** `FontFallback(element, requestedFontId, REASON_HOST_BUNDLED_UNSUPPORTED)` is added per element
(Time / Date / Info all go through `resolveText`); `REASON_NEEDS_API_26` keeps its own wording ("needs Android 8") so the
user can tell "this Android is too old" from "this launcher cannot use bundled fonts". The stored `fontId` is never
rewritten (unit test + codec round trip; schema stays 2). The notice is one line per requested font, not per element, and
the weight note ("900 shows as 700") is suppressed for a fallback font (Studio rebuilds the panel after the first render
tells it the host fell back; a Robolectric test covers reopening a saved Poppins design).

**Parity by construction.** Provider (`environment(context)`) and Preview (`environment(app, forEditor = true)`) both read
the one probe. The resolved spec carries only the effective face, so the composer, `DigitalWidgetFit` (which composes from
that spec) and the placed widget see the same font; no code path measures the requested bundled face. Unit test
`HostFontParityTest` fits Bebas Neue (narrower than system sans) on a fallback host and requires the result to equal the fit
of an explicit system-sans design, while the capable host keeps the requested size.

**moto g13 / API 34 / Motorola Launcher3 (debug APK of this branch, widget id 23, Card).** For each of the six requested
families (requested font set in Studio, saved, widget read on the launcher; Studio Preview vs placed widget):

| Requested | Effective (Preview) | Effective (placed) | Preview time ink w/h | Placed time ink w/h | Studio notice |
|---|---|---|---:|---:|---|
| Poppins | system sans (face view w900) | system sans (w900) | 519x151 = 3.437 | 527x153 = 3.444 | "Poppins -> system font ..." once |
| Varela Round | system sans (w900) | system sans (w900) | 493x150 = 3.287 | 500x152 = 3.289 | once |
| DM Serif Display | system sans (w900) | system sans (w900) | 520x150 = 3.467 | 528x152 = 3.474 | once |
| Barlow Condensed | system sans (w900) | system sans (w900) | 520x151 = 3.444 | 528x153 = 3.451 | once |
| IBM Plex Mono | system sans (w900) | system sans (w900) | 522x151 = 3.457 | 527x152 = 3.467 | once |
| Bebas Neue | system sans (w900) | system sans (w900) | 519x151 = 3.437 | 527x153 = 3.444 | once |

The remaining 1-2% size difference is Preview scale, and ink aspect varies with the minute digits shown at capture time.
Poppins would have resolved to its own w700 face view (its heaviest) had it been effective, so face id w900 in both is itself
evidence. Clipping was checked by eye on the Poppins Preview and placed screenshots only (no clipping); the other five were compared by face id and ink size. After each save the stored request was read
back from `clocky_widget_settings.xml` (`clocky-bebas-neue`, `clocky-plex-mono`: the requested id was kept, not rewritten to
system sans). The widget was returned to its original look afterwards (system sans 900 via the design's token font; the
time colour role came back as PRIMARY instead of ACCENT, which are the same #141414 in this palette).

**Not exercised.** Pixel API 35 / API 30 emulators (none attached), One UI / Nova / Lawnchair, weights other than 400
fragment probe and 900 in Studio, fit at a size where Bebas->system actually changes (covered by the Robolectric fixture,
not by a launcher run), a launcher on which the probe returns `true` (so the "supported" path on a real host is covered only
by same-package/Robolectric inflation plus the unit contract; such a host would show the requested face).

**Deferred.** The platform system-family inventory (section 5 of the brief) was not run: it is optional, and parity was the
gate. System-font variety stays at the Phase 1B/2 set.

## 2. 3A — Responsive core

### 2.1 Purpose

Replace the two-class (Strip / Card) system with the four End-State classes, make per-class overrides general,
and let API 31+ launchers select existing entries during resize independently of app regeneration.
Callbacks may also arrive during a held drag (3A-0 owner-approved premise correction).

### 2.2 Baseline before 3A-1 (checked in the code, 2026-10-07)

Phase 2 removed the `SizeClassPatches` helper and the Phase 1A editor classes. The following historical baseline
is superseded for model/codec/resolver/edit semantics by the 3A-1 record below. Provider/Preview keep its
two-class render selection explicitly until 3A-2.

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

| Constant | Measured value (3A-0) | Status |
|---|---|---|
| `STRIP_MAX_H` | 100 dp | fixed for the measured host matrix |
| `LARGE_MIN_H` | 160 dp | fixed for the measured host matrix |
| `SQUARE_RATIO` (w < ratio × h) | 2.5625 | fixed for the measured host matrix; minimum ratio margin 0.100379 |

Ruling R1: one class per widget, used for both orientations. Measured input is
`MAX_WIDTH` / `MIN_HEIGHT` in dp (landscape-like options pair), not the current entry bounds.
Check Strip first, then Square, then Large, otherwise Card. Production still keeps the Phase 1A
Strip/Card resolver until 3A-1; the spike does not enable Square/Large. See §2.10 for measured coverage.

**(b) General Patch.** Model/codec/resolver/edit API implemented in 3A-1; four-class UI integration deferred (End-State §9):

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
  `date.gapDp` (End-State §5.2 "Time との間隔"; nullable base value added in 3A-1, preserving the historical
  4dp-with-tokens / 0dp-without-tokens default when absent).
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

- **API 31+:** `RemoteViews(Map<SizeF, RemoteViews>)`, **Keying A**: only the sizes reported in
  `OPTION_APPWIDGET_SIZES`, each fitted to its real key. The measured hosts report 2 (moto) or 3 (API35) entries.
  All reported entries resolve the same R1 widget class. B's extra anchors exceed API35 bitmap memory at 4×4,
  increase generation cost, and do not guarantee ratio-based Square switching (§2.10).
  Hosts may call the app during a held drag; do not assume callbacks occur only after drop.
- **API 23–30:** unchanged mechanism: `onAppWidgetOptionsChanged` → `RemoteViews(landscape, portrait)`, each half
  fitted to its own size, the class taken from the R1 rule.
- The fit (`DigitalWidgetFit`) and composer are reused per entry. Nothing ticks: every entry carries TextClocks.

**(d) Metadata** (`res/xml/digital_appwidget.xml` and `xml-v28`): `targetCellWidth/Height` 4×2 (already in v28),
`minResizeWidth/Height` lowered toward 2×1 per End-State §9, `widgetFeatures="reconfigurable"` (already), no
`configuration_optional`. Lowering `minResizeWidth` from 250dp is gated on 3A-0: it exposes sizes (2×1, 3×1, 2×2)
that require template verification. 3A-0 found SPLIT→Strip cannot inflate its plain View spacer:
**keep production minResizeWidth/Height at 250/40dp until fixed and retested in 3A-2**.
`minWidth` stays 250dp (the default placement).

#### 3A-1 implementation record (2026-10-08)

Starting main: `15da85ae67c82ddf82f1f0b51d6fedd1b6703582` (#47 merged; #44–#46 also verified merged).
This PR is **model / codec / resolver / edit semantics only**. No diagnostic source was cherry-picked.

- `SizeClass` adds Square/Large after Strip/Card (no SizeClass ordinal persistence was found).
  `SizeClassRule` uses MAX_WIDTH / MIN_HEIGHT and the measured constants above; invalid, missing,
  NaN/infinite/nonpositive inputs fall back to Card. The 30 distinct measured host/cells are a checked-in
  test fixture derived from §2.10's raw table, with height and ratio boundary tests.
- `DesignLayout.inheritedPatchFor` merges fields through Square/Large→Card→base. `patchFor` remains **own
  patch only**, so badges/revert distinguish ownership from inherited values. `DesignResolver` accepts
  an explicit widget class, with the measured rule as its pure default; an entry's fit bounds do not decide it.
- Typed LayoutPatch adds Time/Date/Info alignment, Info size/offset/visibility, padding and Date gap.
  Schema stays 2; no migration step, built-in version or built-in snapshot changed. `DateElement.gapDp`
  is nullable and omitted when unset; `InfoElement.visible` defaults true and is omitted at that default.
- Strip/Card Time/Date weight remains readable/writable. Square/Large own weight creation is rejected
  by model/edit APIs. Imported Square/Large weight paths are preserved as inert raw JSON; they do not
  replace inherited Card weight. Identity fields (font/color/opacity/spacing/background/effects/behavior)
  have no active typed override and foreign paths are retained without applying them.
- Unknown class payloads (`preservedOverrides`) and patch paths (`preserved`) retain canonical JSON values,
  including objects, arrays, null and scalar types. Whitespace is not a schema property. Unknown base and
  patch template names stay `requestedTemplate`, with TIME_FIRST effective fallback and a disclosed
  `TemplateFallback`. A deliberate known-template edit replaces that scope's request; editing unrelated
  fields does not. Revert restores inheritance rather than copying parent fields into the child.
- DesignEdits reads inherited values and writes only the chosen scope. Info/alignment/padding/gap APIs
  support all classes. Finite requested offsets are kept; the effective resolver retains SDK/RTL and
  conservative half-min-width/half-min-height clamping, without changing saved values. Classification's
  MAX_WIDTH / MIN_HEIGHT pair is not substituted for current layout bounds. Entry-fit integration is 3A-2.
- Quick Tune and Studio preparation scale Info patch sizes as well as Time/Date sizes; they preserve
  future payloads. Resetting known Layout fields retains unrelated future paths/classes. Explicit whole
  design reset still replaces the document with the selected reference.
- **Compatibility staging:** provider and Preview explicitly pass the existing height-only Strip/Card
  render class. Their paired RemoteViews / fit / composition path remains unchanged. Studio keeps the
  two-class selector and global Info UI; new four-class edit semantics are tested at the API boundary.
  Square/Large weight controls are suppressed for scoped edits; global weight remains editable.
  Four-class selector/Preview integration and new layouts remain 3A-2.

Validation: `testDebugUnitTest` (416 tests, zero failures/errors), `lintDebug` and `assembleDebug` passed.
Existing SDK23/28/34 render tests and new legacy-render-policy regressions guard this staging boundary.
moto g13/API34 in-place Phase 2 upgrade: widget ID 23 retained, settings byte-identical, no Clocky
crash/ANR; stable before/after screens retain the same background, faces and geometry (time advances).
[PR-safe cdev summary](../measurements/phase3a1/moto-upgrade-summary.md); session finished.
Raw screenshots/settings stay local; no API31 rotation or final four-class render parity was exercised.
**3A-1 complete; 3A-2 can start after owner merge.** 3A-2 must implement keying A, goAsync/worker generation,
first-update/end-to-end latency checks, SPLIT Strip spacer fix, metadata gating, enabled-host API31+
rotation and final four-class Preview/widget parity. None of those is implemented here.

### 2.4 Threshold measurement method and decision (3A-0)

**Completed 2026-10-08:** input `MAX_WIDTH` / `MIN_HEIGHT`; constants 100dp / 160dp / 2.5625.
All 30 distinct measured host/cells match intended classes (42 orientation observations).
The following historical provisional-rule conflict motivated the measurement:

| Host | Size | Ratio w/h | End-State class | Intended |
|---|---|---|---|---|
| moto g13, Motorola Launcher3, 4×2 fresh add | portrait 363×260 dp | 1.396 | **Square** (< 1.4) | Card |
| same | landscape 667×132 dp | 5.05 | Card | Card |
| Issue #31 device, fresh add | 268×191 dp | 1.403 | Card by 0.003 | Card |

So with portrait sizes the default 4×2 on the reference device would become Square; with landscape sizes a 2×2 will
look wide. Physical portrait aspect ratio is therefore not the selected rule input; measured
landscape-like options and a calibrated ratio separate the intended cells (full data in §2.10).

Completed procedure (Issue #31 style diagnostic build):

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

Framework limits considered in the spike (measured application/rejection results in §2.10):

- A size map has at most **16** entries; all entries must come from the same package.
- Total bitmap memory of one update ≤ **screen width × height × 4 bytes × 1.5**
  (moto g13 720×1600: ≈ 6.6 MiB; API 30 emulator 1080×2280: ≈ 14.1 MiB). Exceeding it throws.
- Binder transaction buffer ≈ 1 MiB per process, shared; large bitmaps travel as ashmem, the rest of the parcel
  does not.

Historical **pre-spike estimate**, superseded by the measured table in §2.10
(gradient or outline background is the only bitmap; text is TextClock):

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
  Generation budget includes **fit + compose**, not just compose. Keep p95 ≤ 100 ms per entry as an
  optimization target and ≤ 400 ms per update as the production target; 3A-0 A entry p95 exceeded 100 ms
  (moto 180.178 / API35 122.223 ms), while updates were close to 400 ms. **goAsync is required** in 3A-2,
  with production-equivalent first-update and end-to-end timing including send/IPC before shipping.

Completed spike procedure (branch `spike/3a-size-map`, never merged):

1. Build the map with A and B behind a debug flag. Log per entry: key size, class, compose time (`nanoTime`),
   bitmap bytes, and parcel size (`writeToParcel` into a `Parcel`, `dataSize()`).
2. Worst-case design: gradient background, Info line, seconds, AM/PM, shadow.
3. Hosts: API35 emulator and moto g13 (API34). Optional API31–33 was not exercised: no installed image.
4. Checks: (i) separate callback timing from host entry selection: callbacks can arrive during a held drag.
   Suppress debug map regeneration to observe selection of existing entries independently of app rendering.
   This replaces the after-drop callback premise with owner approval (2026-10-08).
   (ii) rotation picks the right entry (API31+ rotation not exercised on the available homes);
   (iii) no `TransactionTooLargeException` / bitmap-memory `IllegalArgumentException` in `cdev logs`; (iv) after
   `cdev proc kill` of Clocky the TextClocks keep ticking; (v) preview = widget for each class.
5. Measurement exit: A chosen and measured numbers recorded in §2.10. Production shipping remains gated
   on asynchronous generation/budget verification and the SPLIT→Strip fix; diagnostic compose timings alone
   do not certify shipping budgets. If neither works on a host,
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

#### 3A-2 implementation record (2026-10-08)

PRs #44–#48 were confirmed merged before production implementation. Owner-authorized #48 merge
refreshed main to `7b7ab11bbd0efc38d733b14383d8591b62b60a20`.

- Production `SizeClassRule` runs once from MAX_WIDTH/MIN_HEIGHT. Constants100/160/2.5625 and
  precedence unchanged. API31+ uses distinct valid OPTION_APPWIDGET_SIZES keys only (maximum16);
  all share the widget class and fit actual fractional bounds. Missing/malformed lists use the
  historical pair, never synthetic map anchors. API23–30 always uses the pair/common class.
- SPLIT Strip now uses a supported weighted TextView spacer. Actual apply fails before and passes
  after on SDK23/28/31/34/35. Original300-case and fractional-key geometry audits pass.
- Render broadcasts use goAsync/finish for success, failure and replaced/stale work. One worker
  expires after one second idle. Pending work coalesces per widget; generation check and send share
  a lock, preventing stale overwrite. Deletion invalidates jobs. No service or app ticking loop.
- Preview/Gallery/Quick Tune/Studio use four classes and the production resolver/fit/composer.
  Matching host geometry wins; otherwise measured moto pairs represent4×1/4×2/2×2/4×3. Info scope,
  layout padding/alignment/offsets and date gap are connected; Square/Large scoped weights absent.
  R2 inheritance includes SPLIT Card spacing. Effective-font and localized AM/PM contracts retained.
- Widest samples are generation-local and keyed by effective face/style/format. A two-pixel fit
  gutter covers native shaping rounding; bitmap quality unchanged. Debug timings split resolve/
  fit/compose, RemoteViews construction, send and queue-inclusive end-to-end; additional local
  apply/bitmap/Parcel diagnostics happen after the production timestamp.
- **Release metadata retained at250×40dp**: candidate110×40dp passed API30 after the template gate,
  and subsequently moto also passed2×1/2×2/3×1/3×2 and fresh placement/picker. API35 performance/restoration
  gates remain open. Default minWidth250dp and
  targetCell4×2 preserved. No widening is claimed in the submitted metadata.
- API30: IDs/settings, pair rotation, resize, scoped UI, ticking, fresh4×2 and Time/Date four-class parity verified.
  moto owner unlocked: Keying A/all-class stress parity, resize including held callback/send,
  real SPLIT2×1,4×4 worst-case bitmap/send,3A-1 upgrade and process-absent ticking verified/restored.
  n30 per-class generation p95183.81/241.52/251.80/374.68ms,first latency recorded. Final ID23/settings
  byte-identical and original4×3 geometry restored,cdev finished. No moto home-rotation preference change.
  API35 parallel report confirms functional map/memory/parity/ticking/rotation, but p95 Card427.57ms /
  Square671.30ms exceeds400ms; final restart/pixel/settings restoration completed, but existing AOSP alarm
  PendingIntent mutability crash reproduced on the PR APK. Final report linked in RESULTS. AOSP-domain
  fix requires owner ruling; do not treat an existing failure as a passed no-crash gate.
  Detailed first/phase timings,limitations and owner-authorized direct binary-screenshot ADB exception
  are recorded in RESULTS. No new
  built-in content/Stacked/Corner preset, schema or provider rename.

Evidence and remaining gates: [`phase3a2/RESULTS.md`](../measurements/phase3a2/RESULTS.md).
**Phase3A is not complete**; Phase3B/3C remain gated by owner merge after required verification.

### 2.10 Measurement results

Completed 2026-10-08 against main `dddd16c7dbfdf79129cf0f763812e33ecd575fc8` (PRs #42–#46 merged).

**Full raw cell table, candidate inputs, boundaries, timings, live-resize logs/screens, template failures,
regression and device evidence:** [3A-0 measurement record](../measurements/phase3a0/RESULTS.md).
Companion safe derived data: [222 raw input rows](../measurements/phase3a0/raw-inputs.csv),
[performance samples summary](../measurements/phase3a0/performance.json),
[300 local template audit cases](../measurements/phase3a0/template-audit.csv),
[306 per-entry samples](../measurements/phase3a0/per-entry.csv),
[91 per-update samples](../measurements/phase3a0/per-update.csv),
[72 reachability/orientation rows](../measurements/phase3a0/reachability.csv).

| Decision | Result |
|---|---|
| R1 input | `MAX_WIDTH` / `MIN_HEIGHT` dp; one class per widget across orientations |
| Constants | Strip 100dp; Large 160dp; Square ratio 2.5625; Square precedes Large |
| Coverage | moto API34 9 portrait; Pixel API35 9 portrait; Pixel API30 12 portrait + 12 landscape |
| Intended 4×4 | Square (owner shape-priority clarification) |
| A / B | **A**; B 4×4 API35 bitmap rejected: 18,534,224 > 15,552,000 bytes |
| A bitmap / parcel, 4×4 | moto 4,689,984 / 5,148 bytes; API35 14,203,708 / 7,460 bytes |
| A p95 entry / update fit+compose | moto 180.178 / 388.834 ms; API35 122.223 / 386.078 ms (30 updates each) |
| Production minResize | **NO** until SPLIT Strip's forbidden View spacer is fixed/verified in 3A-2 |
| goAsync | **YES**; first update/end-to-end production budget still needs verification |
| Live resize premise | callbacks during held drag allowed; API35 freeze proves host-only existing-entry selection |
| Ticking / regression | all 3 hosts process-absent ticking; byte-identical settings restore proofs; IDs retained |
| Not exercised | API31+ home rotation, API31–33, unavailable launchers; 5-column cells unreachable on both 4-column hosts |

The rule fits the measured matrix with ≥23dp height and ≥0.100379 ratio boundary margins. It is not a
universal launcher claim. Current-template geometry passed 280 local cases; 20 SPLIT Strip cases failed
RemoteViews inflation, so the metadata shipping gate is still closed. Final four-class layouts/parity
are 3A work. **3A implementation can start** with those documented 3A-2 gates; no production work is in this PR.

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
| Lowering `minResize` exposes sizes the templates cannot apply/fit | Keep 250/40dp release minResize; fix SPLIT Strip forbidden View spacer and verify templates in 3A-2 |
| Fit/generation stalls main thread | goAsync in 3A-2; verify first update and end-to-end 400ms target, not only compose |
| Callback arrives during held drag | Supported measured behavior; separate callback-driven updates from host-only entry selection |

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
| R1 | What input decides the size class? | (a) one class per widget from one orientation, as Phase 1A; (b) per orientation / per `SizeF` entry (End-State §9 literal; portrait and landscape can differ) | (a) for predictable overrides. 3A-0 measured MAX_WIDTH / MIN_HEIGHT with 100dp / 160dp / 2.5625 (§2.10); 4×4 is Square by owner clarification |
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

2026-10-08 3A-0 amendment: §9 now specifies measured input MAX_WIDTH / MIN_HEIGHT and constants
100dp / 160dp / 2.5625, 4×4 Square, keying A, callbacks during drag, and the minResize template gate.
The historical 2026-10-07 bullets above record their original provisional status.

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
