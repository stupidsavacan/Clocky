# Web Agent Handoff

GitHubを唯一の正本として、通常ChatのWeb/GitHub作業を継続するための引き継ぎメモです。作業開始時は、この文書のSHAやrun番号を盲信せず、必ずGitHubから `main` / open PR / Actions を再取得してください。

## Latest confirmed main baseline

- main: `d2161533c0737e7ee5f26e60ee0a2dea73b1bd5a` (PR #25 merge)
- Current App CI #171: https://github.com/stupidsavacan/Clocky/actions/runs/31796560329 — **success**
  - exact checkout: `d2161533c0737e7ee5f26e60ee0a2dea73b1bd5a`
  - dependency resolution / `compileDebugKotlin` / unit tests / `lintDebug` / lint gate / `assembleDebug` / APK hash: success
  - lint text errors: 0
  - lint XML errors: 0
  - debug APK: `app/build/outputs/apk/debug/app-debug.apk`
  - debug APK SHA-256: `3abc0e34ee91b77cc3fd49563c20cb589d76edd164dfe021601bbbd187eec82e`
  - APK/report artifact upload failed only because GitHub Actions artifact storage quota was exhausted. This is nonfatal under the existing project policy because build/test/lint/assemble/hash succeeded.
- Reference APK Guard #328: https://github.com/stupidsavacan/Clocky/actions/runs/31796560267 — **success**
- At the migration investigation start point there were no open PRs. Re-query before creating or merging work because concurrent changes are possible.

PR #25 head previously produced SHA-256 `819589049299b2d0f647e456d5d49711d65b7c152efa2341fb27a77d353493f2`; do not confuse that PR-head artifact with the newer post-merge main hash above.

## Recent PR status

- #20 — closed, not merged; superseded by #22 because its old base could revert letter-spacing UI.
- #21 — merged; Digital Widget letter-spacing rendering + settings UI.
- #22 — merged; size settings UI integrated on top of letter spacing.
- #23 — merged as `ed3095048e2a7bcc3fd7e7b3062641e32d170bfa`; Digital Widget model-backed X/Y offsets connected to rendering.
- #24 — merged as `41c8747fe20a4f21313a39865b0ab3c7c046515b`; README/backlog/handoff post-#23 docs sync.
- #25 — merged as `d2161533c0737e7ee5f26e60ee0a2dea73b1bd5a`; non-finite base/profile X/Y values are normalized to 0dp before persistence while nullable profile inheritance is preserved.

## Implemented Digital customization path

Do not duplicate these features:

- per-widget settings store wired into the Digital AppWidget provider
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
- non-finite base/profile X/Y normalized to 0dp before persistence and sanitized again at the renderer boundary
- resolver/editor/renderer/model regression tests for the merged path

X/Y **editing UI** is not implemented. Renderer/model support does not define its editor range/interactions.

## Current repository facts that must not be guessed

- `docs/spec/` contains only `FONT_WEIGHT.md` at the last confirmed inventory point.
- `WidgetSettingsStore` is the new source of truth and persists schema-versioned JSON per `appWidgetId` in `clocky_widget_settings`.
- Current key shape is `widget.<appWidgetId>.settings`; current store code writes schema `1`.
- Model/storage fields alone are not sufficient evidence for new color/opacity, alignment, hour-mode/leading-zero, background, or X/Y editor semantics.
- `docs/parity/WIDGET_PARITY_SPEC.md` describes intended dimensions but does not by itself define all alpha-composition, RTL/alignment, background/API-fallback or editor interaction rules needed for implementation.
- Do not write new settings back into the old MVP primitive key space. Legacy keys, when eventually migrated, are import-only input.

## Old MVP settings migration investigation — blocked on authoritative source readability

Architecture explicitly names `ci/Clocky_MVP_source.zip` as the retained runnable MVP source bundle and source of truth for the old primitive key names until a migration reader exists.

Latest GitHub facts for that bundle:

- path: `ci/Clocky_MVP_source.zip`
- size: **14,299 bytes**
- Git blob SHA: **`6d63be2463ec408824a9b06eec282153c1a2df55`**
- commit `23338240ae55a358c0fae0b443b383cd13169628` (`Update MVP source bundle`) updated that binary after the earlier Base64 helper was committed.

A stale helper exists at `ci/source-b64/part-00.txt`, but it predates the final source-bundle update and therefore **must not be substituted for the final ZIP** when proving migration semantics. Historical `agent/mvp-*` branches checked during the investigation did not expose the final complete settings source as authoritative plain text.

The connected GitHub reader can enumerate the final ZIP's exact SHA/size but rejects the binary blob because the read path accepts UTF-8 text only. Therefore the Web-only investigation cannot prove the complete final old-MVP mapping from the authoritative source bundle. In particular, do not infer or implement any of the following from the stale helper or memory alone:

- complete SharedPreferences file/key list and exact value types/defaults
- old enum/integer mappings and invalid-value behavior
- background-mode mapping
- ARGB alpha versus any separate opacity semantics
- date-format mapping
- whether every setting is strictly scoped by `appWidgetId`
- whether the final old source had profile-specific persistence
- exact one-time/idempotent migration behavior needed to avoid legacy values being re-imported after the new setting is edited/deleted

**Decision:** no partial migration reader is added until those facts can be proven from the final source. This avoids silently corrupting or reviving settings through guessed mappings.

### Minimum Desktop-side evidence needed to unblock migration

No APK, proprietary assets, device data, JKS/password, or private logs are needed. From the repository-owned `ci/Clocky_MVP_source.zip`, extract only the plain-text source files that define:

1. SharedPreferences name and per-widget key construction;
2. every persisted setting key, value type and default;
3. enum/integer/string mapping for alignment, hour mode, date format and background;
4. color/alpha/opacity interpretation;
5. save/load/delete/reset behavior and whether legacy values can reappear;
6. any profile/size-specific persistence semantics.

Record the extracted file paths plus their text (or add a reviewable plain-text provenance note generated directly from those source files) so a future Web agent can map old values to `WidgetSettings` without guessing. Do not provide or commit proprietary reference APK/assets.

## Next Web-safe audit

After re-fetching GitHub state:

1. Inventory current customization model/storage tests for deterministic serialization/normalization gaps.
2. Inspect the fact that JSON writes a schema field and verify whether repository docs/tests define behavior for unsupported/missing schema before changing decoder behavior.
3. Audit renderer/config activity tests for already-defined branches in weight/date visibility/size/letter spacing/X/Y behavior.
4. Audit README/backlog/architecture paths and claims against the current tree; correct stale documentation without inventing missing files or semantics.
5. Only implement parity fields when current repository sources define their exact behavior. Otherwise leave them explicitly blocked rather than guessing.
6. Keep PRs single-purpose, re-check open PRs immediately before branching/merging, and verify required Actions before merge.

## Desktop-only / not verified by Web work

Web/GitHub validation must not be described as device validation. The following remain outside this workflow:

- ADB
- physical device testing
- emulator testing
- launcher E2E / resize interaction E2E
- Google Clock reference APK or proprietary assets
- JKS/password/release signing
- private dumps/logs
- production release decisions

For UI/rendering work, GitHub Actions can validate compile/unit/lint/assemble/hash, but actual launcher appearance, clipping, host-specific RemoteViews behavior, resize interactions and touch UX remain device/reference verification items.

### Current device/reference checklist

- [ ] API 31+ real launcher: verify independent time/date X/Y translation and clipping.
- [ ] API 23–30 real launcher: verify effective 0dp X/Y fallback visually.
- [ ] Resize between compact/regular profiles: verify host-size transitions and per-field inheritance behavior.
- [ ] Verify Digital weight/date visibility/size/letter-spacing controls visually and interactively.
- [ ] Validate targetSdk 35 runtime-sensitive Alarm/Timer/notification/full-screen/direct-boot behavior.
- [ ] Perform Google Clock side-by-side geometry/parity measurement only in an allowed device/reference workflow.
