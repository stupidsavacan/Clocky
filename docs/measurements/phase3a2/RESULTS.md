# Phase 3A-2 production render / platform verification

Date: 2026-10-08. Branch: `feat/phase3a2-responsive-render`, based on merged #48/main
`7b7ab11bbd0efc38d733b14383d8591b62b60a20`.

**Status: implementation present; required moto/API35 shipping evidence incomplete. Phase 3A is not complete.**
The owner authorized merging #48 after green CI; #44–#48 were merged before production work.
No owner ruling/threshold/schema/provider change was needed.

## Render contract and audit

API31+ uses RemoteViews(Map<SizeF, RemoteViews>) for valid host-reported entries only (Keying A).
MAX_WIDTH/MIN_HEIGHT classify once; all entries share the class and fit their own fractional bounds.
API23–30 keeps the landscape/portrait pair with that same class. Missing/invalid API31+ lists fall
back to the pair; no synthetic keys. Effective fonts are resolved before fit, once per environment.
R2 Square/Large→Card→base inheritance retained; SPLIT Card gap behavior applies to both classes.

The SPLIT Strip fixture reproduces moto2×1 bounds173×122dp /325×58dp: actual RemoteViews.apply
failed on SDK23/28/31/34/35 before replacing the plain View spacer, and succeeds afterwards.
A weighted TextView is on the RemoteViews allowlist; layout spacing intent remains.

| Audit | Coverage | Result |
|---|---|---|
| Original measured options matrix | 5 templates ×30 measured cells ×2 orientation entries =300, each SDK28/31/34/35 | 1200 cases, failure0 |
| Host-reported fractional keys | 345 entries/templates per SDK31/34/35, all measured host rows | 1035 cases, failure0 |
| Explicit SPLIT Strip apply | SDK23/28/31/34/35 | pass after fix; fail before |

Each geometry audit verifies actual apply, natural fit, text rectangles within bounds and no
text overlap at widest time/date/AMPM/UTC Info samples. Native Robolectric graphics are used for
geometry; this is not a real launcher visual audit. Fixtures derive from 3A-0 measured rows.
No new templates/built-in versions added; Stacked/Corner and library content stay in later work.

## Async / ordering

Render broadcasts call goAsync. Completion accounting includes invalid/empty inputs, replaced
pending work, stale running work and errors. A single expiring worker drains latest pending work
per widget; generation token validation and updateAppWidget share a lock. Deletion invalidates
work. Tests cover coalescing, concurrent stale publication, failure, executor rejection and actual
PendingResult completion. The worker expires after1s idle; no service or ticking loop.

## Production timing (API30 Pixel emulator / NexusLauncher)

30 updates per measured geometry using the same production queue/generate/updateAppWidget path.
Debug-only ephemeral design: CENTER_STACK, gradient, strong shadow, seconds, suffix AM/PM,
UTC Info and requested clocky-poppins → effective system-sans. Info is hidden by Strip policy.
The diagnostic applies views and measures allocation/Parcel only AFTER send/end timestamp.
Timings below are native production generation (including environment/resolve/fit/compose/map
construction), send boundary, and queue-inclusive end-to-end. No diagnostic apply time is included.

| Geometry | First generation | p95 generation | First send / p95 send | First end-to-end / p95 | Bitmap allocation | Parcel |
|---|---:|---:|---:|---:|---:|---:|
| Strip130×97 /249×53dp |460.34ms|291.37ms|5.62 /23.53ms|473.17 /305.20ms|776,568B|4,316B|
| Card276×210 /514×123dp |248.07ms|122.07ms|18.31 /55.19ms|268.81 /170.38ms|3,662,148B|5,036B|

Nearest-rank p95 over30 samples. First means first harness update, not controlled cold-start.
Strip first exceeds400ms; p95 is below400ms. A first package-update generation log was roughly
585ms (stale during subsequent callbacks); startup/font/framework warming remains a latency risk.
Do not generalize API30 values to moto/API35. Generation-local widest-string reuse avoids duplicate
sampling between entries; no bitmap quality reduction or budget relaxation introduced.

The debug activity is `com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity`:
launch using cdev `launch activity` with `--ei appWidgetId <existing-id> --ei samples 30`.
It saves private `files/phase3a2-performance.json`; retrieval must use cdev adb. Exit/back before
restarting a measurement run. The override is never persisted, and saved design is queued for
restoration afterwards. Ordinary debug provider logs include first-update and entry timings.

## Bitmap / Parcel gate

Keying A allocation quality is unchanged. An SDK35 native regression test replays measured4×4
keys at2.625 density with gradient/Info/seconds/AMPM/shadow/bundled-font fallback, enforcing ≥5%
headroom under the measured15,552,000B limit. Observed native allocation14,203,708B leaves8.67%
headroom. Native Robolectric parcels inline pixel data (14,213,628B), so the test bounds map/action
overhead above bitmap bytes to32KiB; this is not a real Binder Parcel measurement. This protects allocation regression,
**not actual launcher acceptance**. Required real API35 4×4 worst-case send and performance remain
unexercised; no shipping bitmap claim is made from Robolectric.

## Build verification

Robolectric SDK35 requires JDK21; CI now selects21 while application bytecode remains17.
Native SDK/template coverage exceeded Gradle's default512MiB heap in the full suite; test forks
use2GiB and recycle after20 test classes. No production dependency added. Final full run: `testDebugUnitTest lintDebug assembleDebug` green;468 tests,0 failures/errors,
including existing model/codec regressions. Lint has existing warnings but no errors.

## API30 device evidence

Prepared/finished cdev session `s-20261008-081000-emulator-5562`, API30 Pixel emulator,
NexusLauncher, localeen-US,440dpi. Baseline → in-place installs → final comparison: IDs5/7
retained and settings byte-identical; no Clocky crash/ANR. Installed tested APKsha256
`b583cea59c4076f449ed59f87fad9e88fde86a8816821149d3dc108bf23f7d82` was an intermediate3A-2
build with candidate metadata110dp; later Gallery/reset and retained-metadata edits were verified in a second session below. Raw screenshots/settings stay ignored; only cdev summaries are committed.

Final source verification: cdev session `s-20261008-084339-emulator-5562` prepared and finished.
APKsha256 `179ed57e293a34490db434e24b924c4ae4b2d16382da449361fb47954b937bf2` (retained250dp
metadata) installed in place: IDs5/7 and settings byte-identical, no crash/ANR. Picker displays Clocky
Digital4×2. After `proc kill`, ticking11:45→11:46 in35.9s with6/6 process-absent checks.
Fresh placement itself was not performed; existing widgets were preserved.

- Portrait/landscape pair displayed correctly; original rotation settings restored.
- Candidate minResize110×40dp allowed2×1,2×2,3×1,3×2; restored4×2. Resize generated intermediate
  callbacks/classes during the gesture. API30 has no SizeF map host-selection behavior.
- `proc kill`, then `widget --ticking --no-process --index1 --timeout60`: visible time11:13→11:14,
  nine process checks absent. TextClock continues ticking in the host.
- Studio showed four selector buttons; scoped Square/Large Time weights absent; Info scope visible.
  Temporary draft changes discarded; final settings compare SAME.

Shared-path tests establish effective-spec/geometry parity for all four classes; real placed/Preview
visual comparison for all classes remains open. Sample alarm text remains the existing editor-only
placeholder when no real alarm is available. Square/Large own weight editing is hidden; global
weights remain editable. Info scoped patches edit visibility/size/offsets/alignment; color/font/opacity remain global.
No forbidden Info scoped-weight field is introduced.

## Metadata decision

The submitted production metadata explicitly retains minResize250×40dp. Candidate110×40dp
passed the template gate and API30 resize checks, but moto/API35, fresh-placement default,
picker and final-build upgrade checks are not complete. Default minWidth250dp/minHeight70dp,
targetCell4×2, provider component and schema2 remain unchanged. Do not widen release metadata
on this evidence alone.

## Enabled hosts / not exercised

| Host | Current ability | Remaining evidence |
|---|---|---|
| moto g13 API34 / Motorola launcher | prepare rejected KEYGUARD_SHOWING; owner unlock requested | all production3A-2 device gates |
| Pixel API35 emulator5560 | prepare rejected; package/activity services unavailable, owner recovery requested | map4×4 bitmap/send/timing, upgrade/ticking, rotation/live resize, parity |
| Pixel API30 emulator5562 | prepared, verified and finished | fresh placement; full four-class visual parity |
| API31–33 /API25 | not exercised | optional boundary/regression coverage |

No device unlock, launcher-setting change on moto, wipe, reboot, uninstall, clear, log reset or
force-stop was performed. All device commands used cdev. Package-install-related lifecycle
force-stop lines in logs are system installation behavior, not an issued force-stop command.

Required next steps: recover enabled hosts; prepare/baseline/in-place install/compare/finish;
record production p95 and first update, API35 worst-case bitmap/Parcel/send, host rotation/live
resize, four-class Preview/widget parity and process-absent ticking. CI green alone does not
close these gates. Phase3B/3C may start only after owner merge of completed3A.
