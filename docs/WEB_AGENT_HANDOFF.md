# Web Agent Handoff

GitHubを唯一の正本として、通常ChatのWeb/GitHub作業を継続するための引き継ぎメモです。実際の状態は作業開始時に必ずGitHubから再取得してください。

## Latest confirmed main

- main: `ed3095048e2a7bcc3fd7e7b3062641e32d170bfa` (PR #23 merge)
- Current App CI #167: https://github.com/stupidsavacan/Clocky/actions/runs/31795204119 — **success**
  - dependency resolution / compile / unit tests / lint / lint gate / `assembleDebug` / APK hash / post steps: success
  - lint text errors: 0
  - lint XML errors: 0
- Reference APK Guard #317: https://github.com/stupidsavacan/Clocky/actions/runs/31795204031 — **success**
- main debug APK path: `app/build/outputs/apk/debug/app-debug.apk`
- main debug APK SHA-256: `a9dfb93698b4f7cb49654b7fb78259c5a07a5d32e964bda77f60613f99e36f5b`
- GitHub Actions artifact upload failed because repository artifact storage quota was exhausted. This is nonfatal under the existing policy because build/test/lint/assemble/hash all succeeded.

For comparison, PR #23 head `6c02a9e5240d20c2980d58d1b99bd19185342606` produced SHA-256 `dd66fb0812fb21cd14a233577bc31f192f4de8bf877bc2c2aacfac0d812177be`; do not confuse that PR-head artifact hash with the post-merge main hash above.

## PR status

- #20 — closed, not merged; superseded by #22 because its old base could revert letter-spacing UI.
- #21 — merged; Digital Widget letter-spacing rendering + settings UI.
- #22 — merged as `680315583c2948d0b8856245763be6fe8504da7f`; size settings UI integrated on top of letter spacing.
- #23 — merged as `ed3095048e2a7bcc3fd7e7b3062641e32d170bfa`; Digital Widget model-backed X/Y offsets connected to rendering.
- At the post-#23 inventory point there were no open PRs. Re-query before starting new work because concurrent changes are possible.

## Implemented Digital customization path on main

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
- non-finite X/Y values sanitized to 0dp at renderer boundary
- resolver/editor/renderer regression tests for the merged path

PR #23 specifically leaves X/Y **editing UI** out of scope; renderer/model support does not by itself define editor ranges/interactions.

## Repository truth discovered during post-#23 inventory

- `docs/spec/` currently contains only `FONT_WEIGHT.md`.
- Do not assume or cite nonexistent MVP customization-plan spec files.
- `FONT_WEIGHT.md` defines requested-vs-effective weight semantics and says editor/preview should disclose quantization when the selected renderer cannot reproduce the request exactly.
- Current Digital config preview already resolves through `DigitalWidgetWeightRenderer.effectiveWeight(...)`, while its displayed weight value is the requested value. A future quantization disclosure is therefore a plausible Web-safe candidate only if implemented without inventing ambiguous visual design.
- Model/storage fields alone are not sufficient evidence for new color/alignment/hour-mode/background UI semantics. Re-read current source/tests/docs before implementing any such field.

## Next Web-safe work

1. Keep README, implementation backlog and this handoff synchronized with the actual post-#23 build state in one small docs-only PR.
2. Re-read `DigitalWidgetConfigActivity`, its layout/strings, `FONT_WEIGHT.md`, weight policy/resolver and tests to decide whether requested→effective weight disclosure has a deterministic, source-defined implementation. If exact UI placement/text cannot be derived without guessing, leave it as an explicit candidate rather than inventing UX.
3. Inventory current model/storage/tests/renderers for other pure logic, serialization/normalization/migration or regression-test gaps whose semantics are already defined by repository sources.
4. Do not create X/Y editor controls, color/background/alignment/hour-mode controls, or Google Clock-like geometry based only on model field existence.
5. Prefer small single-purpose PRs and re-check open PRs immediately before branching to avoid duplicates.

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
