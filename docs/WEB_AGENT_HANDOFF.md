# Web Agent Handoff

GitHubを唯一の正本として、通常ChatのWeb/GitHub作業を継続するための引き継ぎメモです。作業開始時は、この文書のSHAやrun番号を盲信せず、必ずGitHubから `main` / open PR / Actions を再取得してください。

## Current verification scope (owner ruling2026-10-08)

Current acceptance is the owner's moto g13/API34/Motorola Launcher3 personal use. Further non-moto device/API/launcher verification moves to optional Phase 5+; do not restart it without an owner request. Existing failure evidence and automated checks stay. See [VERIFICATION_SCOPE.md](product/VERIFICATION_SCOPE.md) and the current backlog. The baseline/run notes below are historical snapshots, not current verification prerequisites.

## Latest confirmed functional main baseline

- functional main before this docs sync: `59a3cf486f34687399b82875bec24bd8d3b63439` (PR #29 merge)
- Current App CI #180: `31822461864` — **completed/success**
  - checkout: `59a3cf486f34687399b82875bec24bd8d3b63439`
  - dependency resolution / `compileDebugKotlin` / unit tests / `lintDebug` / lint gate / `assembleDebug` / APK hash: success
  - lint text errors: 0
  - lint XML errors: 0
  - debug APK: `app/build/outputs/apk/debug/app-debug.apk`
  - debug APK SHA-256: `0660fdbac1fa63eabbe79e2c2b15cdb613ddca962f67f9c0fb1cd1fb53a96f87`
  - APK/report artifact uploads failed only because GitHub Actions artifact storage quota was exhausted. This is nonfatal under the existing project policy because build/test/lint/assemble/hash succeeded.
- Reference APK Guard #355: `31822461749` — **completed/success**

The latest functional APK hash above is the source-of-truth hash for the PR #29 main state. A later docs-only merge does not by itself create a newer functional APK unless Current App CI actually runs for that docs commit.

## Recent PR status

- #20 — closed, not merged; superseded by #22 because its old base could revert letter-spacing UI.
- #21 — merged; Digital Widget letter-spacing rendering + settings UI.
- #22 — merged; size settings UI integrated on top of letter spacing.
- #23 — merged; Digital Widget model-backed X/Y offsets connected to rendering.
- #24 — merged; README/backlog/handoff post-#23 docs sync.
- #25 — merged; non-finite base/profile X/Y normalized to 0dp while nullable inheritance is preserved.
- #26 — merged; post-merge Reference APK Guard is correctly recorded as `31800832387`.
- #27 — merged; repository-MVP-compatible five-family time/date font rendering while preserving the existing requested-weight path.
- #28 — merged; explicit hour-mode rendering: follow system, forced 12h `h:mm`, forced 24h `HH:mm`.
- #29 — merged; consolidated per-widget settings are deleted when the launcher removes that widget.

## Implemented Digital customization path

Do not duplicate these features:

- per-widget schema-versioned settings store wired into the Digital AppWidget provider
- independent time/date weight rendering
- base/profile weight editor
- date visibility rendering + base/profile settings
- independent time/date size rendering
- base/profile size editor
- independent time/date letter-spacing rendering + editor
- 4×1 / 4×2 per-field nullable profile overrides (`null = inherit`) for supported fields
- independent time/date X/Y offset resolution
- API 31+ RemoteViews X/Y translation
- API 23–30 effective 0dp offset fallback while preserving requested X/Y settings
- non-finite base/profile X/Y normalization/sanitization
- repository-MVP's five proven font families: `sans-serif-light`, `sans-serif-rounded`, `serif`, `sans-serif-condensed`, `monospace`
- explicit time hour mode: system-following / 12h `h:mm` / 24h `HH:mm`
- widget-removal lifecycle cleanup through the current settings store
- resolver/editor/renderer/model regression tests for the merged paths

X/Y **editing UI** is still not implemented, but its range/interaction semantics are now defined in `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md`: template-relative dp offsets, RTL-aware X direction, ±50% size-class bounds, draw-time clamping, and API 23–30 effective 0dp degradation disclosure.

## Current settings store facts

- SharedPreferences file: `clocky_widget_settings`
- key: `widget.<appWidgetId>.settings`
- current JSON schema field: `1`
- current settings are the sole write-side source of truth
- legacy primitive keys are import-only input if a future migration reader is added; never dual-write current values into them

Model/storage fields alone are not sufficient evidence for renderer/editor behavior. Use `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md` as the implementation contract for date-format override semantics, alpha composition, RTL alignment, legacy background mapping, and X/Y editor interaction rules; do not substitute behavior inferred from current field shapes.

## Repository-owned MVP source contract — decoded and verified

The old migration investigation is **not blocked on ZIP readability anymore**. The authoritative repository-owned bundle was read and its relevant plain-text source was inspected.

Source provenance:

- path: `ci/Clocky_MVP_source.zip`
- size: **14,299 bytes**
- Git blob SHA: **`6d63be2463ec408824a9b06eec282153c1a2df55`**
- relevant source files inside the bundle:
  - `WidgetPrefs.java`
  - `WidgetConfigActivity.java`
  - `ClockWidgetRenderer.java`

Verified legacy SharedPreferences contract:

- file: `clocky_widgets`
- per-widget prefix: `w_<appWidgetId>_`
- integer keys: `timeFont`, `dateFont`, `timeSize`, `dateSize`, `timeX`, `timeY`, `dateX`, `dateY`, `align`, `bg`, `hourMode`, `dateFormat`
- boolean key: `showDate`
- string keys: `timeColor`, `dateColor`
- **there is no persisted legacy `leadingZero` key**
- **there are no profile-specific legacy preference keys**

Verified font-index mapping:

```text
0 sans-serif-light
1 sans-serif-rounded
2 serif
3 sans-serif-condensed
4 monospace
```

Verified date-format mapping:

```text
0 M月d日(E)
1 EEE, MMM d
2 yyyy.MM.dd
3 EEE d MMM
```

Verified legacy hour mode:

```text
0 follow device/system
1 explicit 24h -> HH:mm
2 explicit 12h -> h:mm
```

Verified alignment/background intent:

- alignment index 0 = START, 1 = CENTER_HORIZONTAL, 2 = END
- background index 0 = transparent layout, 1 = dark layout, 2 = light layout
- old background layouts also participate in fallback color/adaptive-padding behavior

## Migration status — source decoded; importer contract-unblocked, implementation pending

Do **not** describe migration as blocked by source readability or representability. `CLOCKY_END_STATE.md` §5.8 and `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md` now define the previously missing mappings:

1. **Date format default/override:** `formatPattern: String?`; `null = Locale Auto`.
2. **Color/opacity:** UI/storage treat RGB and opacity separately; saved RGB alpha is normalized to FF, while legacy/imported alpha is folded into opacity.
3. **RTL alignment:** START/CENTER/END only; X follows writing direction and reverses in RTL.
4. **Background mapping:** transparent → None; dark → Solid #111111 at 90%; light → Solid #FFFFFF at 90%, with 18dp base padding / 10dp compact override and legacy light default text #111111.
5. **Leading zero:** old MVP persisted no key. Never synthesize a legacy value; preserve the current default.

Importer rules:

1. Existing consolidated JSON always wins.
2. Read only exact proven legacy keys/types/mappings.
3. Normalize and save through the current `WidgetSettingsStore`.
4. After successful import, remove or permanently ignore that widget's old keys so stale values cannot be resurrected.
5. Never dual-write current values to the old primitive key space.
6. Do not invent profile data or a leading-zero legacy value.

## Remaining Web-safe work boundary

The previously blocked customization work is now actionable under `docs/spec/WIDGET_CUSTOMIZATION_CONTRACT.md`. Web-safe implementation may proceed for:

- X/Y editor range and interaction behavior
- nullable date-format override / Locale Auto
- RGB-alpha and separate-opacity normalization/composition
- START/CENTER/END RTL-aware alignment
- exact legacy transparent/dark/light background migration
- the one-way primitive-key importer
- current leading-zero behavior defined by the End-State, while preserving the absence of any legacy key

Implementation still must not claim device/launcher verification from Web-only checks. Unsupported/missing JSON-schema behavior remains outside this synchronization unless separately specified; do not invent decoder policy merely because a schema field exists.

## Device/reference-only / not verified by Web work

Web/GitHub validation must not be described as device validation. The following remain outside this workflow:

- ADB
- physical device testing
- emulator testing
- launcher add/reconfigure/resize interaction E2E
- Google Clock reference APK or proprietary assets
- JKS/password/release signing
- private dumps/logs
- production release decisions

Current device/reference checklist:

- [ ] API 31+ real launcher: verify independent time/date X/Y translation and clipping.
- [ ] API 23–30 real launcher: verify effective 0dp X/Y fallback visually.
- [ ] Resize between compact/regular profiles: verify host-size transitions and inheritance.
- [ ] Verify Digital weight/date visibility/size/letter-spacing/font-family/hour-mode controls visually and interactively where applicable.
- [ ] Validate launcher add/remove/reconfigure behavior around persisted settings.
- [ ] Validate targetSdk 35 runtime-sensitive Alarm/Timer/notification/full-screen/direct-boot behavior.
- [ ] Perform Google Clock side-by-side geometry/parity measurement only in an allowed device/reference workflow.

GitHub Actions proves compile/test/lint/assemble/hash status only; it does not prove launcher appearance, clipping, host-specific RemoteViews behavior, touch UX, or physical-device parity.
