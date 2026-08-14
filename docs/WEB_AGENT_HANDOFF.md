# Web Agent Handoff

GitHubを唯一の正本として、通常ChatのWeb/GitHub作業を継続するための引き継ぎメモです。実際の状態は作業開始時に必ずGitHubから再取得してください。

## Latest known main

- main: `680315583c2948d0b8856245763be6fe8504da7f` (PR #22 merge)
- Current App CI #156: https://github.com/stupidsavacan/Clocky/actions/runs/31792325814 — success
- Reference APK Guard #298: https://github.com/stupidsavacan/Clocky/actions/runs/31792325769 — success
- Latest confirmed main debug APK SHA-256 (PR #22 CI #155): `6872831969896f93398cd6d70daba26b2c4a1705b6bac345e6aa6b9e28529cd9`
- APK path: `app/build/outputs/apk/debug/app-debug.apk`
- Artifact upload may fail when the GitHub Actions artifact quota is exhausted; build/test/lint/assemble/hash success remains the canonical validation.

## PR status

- #20 — closed, not merged; superseded by #22 because its old base could revert letter spacing UI.
- #21 — merged; Digital Widget letter spacing rendering + settings UI.
- #22 — merged as `680315583c2948d0b8856245763be6fe8504da7f`; size settings UI integrated on top of letter spacing.
- #23 — Draft/open at the time of this handoff; connects model-backed Digital Widget X/Y offsets to rendering. Temporary one-shot workflow and patch script are removed from the review diff.

## Implemented customization path

Already present on main before #23:

- weight rendering + base/profile weight editor
- date visibility rendering + settings
- base/profile time/date size editor
- time/date letter spacing rendering + settings
- 4x1 / 4x2 per-field nullable profile overrides (`null = inherit`) for supported fields

PR #23 adds, when merged:

- base/profile X/Y offset resolution
- API 31+ RemoteViews translation for independent time/date X/Y offsets
- explicit API 23–30 zero-offset rendering fallback while preserving requested settings
- resolver/renderer regression tests

Do not duplicate the features above. Re-read current model/spec/tests before selecting later work.

## Next Web-safe work

1. Finish #23 only after final-head Current App CI and Reference APK Guard are green; record final SHA, run URL, APK path/SHA-256 and Desktop-only limits in the PR body.
2. After merge, verify main push Current App CI + Guard and update this handoff to the new main SHA/hash.
3. Reconcile `README.md` and `docs/IMPLEMENTATION_BACKLOG.md` with the actual green build state; both may contain stale pre-build status.
4. Inventory model/spec/storage/tests for the next explicit, non-ambiguous customization behavior. Prefer pure logic, serialization/normalization/migration, renderer/config regression tests, and other behavior whose semantics are already defined by repository sources.
5. Avoid guessing Google Clock appearance or inventing UX/ranges that are not specified in the repository.

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

For UI/rendering work, GitHub Actions can validate compile/unit/lint/assemble/hash, but actual launcher appearance, clipping, host-specific RemoteViews behavior, resize interactions and touch UX remain Desktop/device verification items.