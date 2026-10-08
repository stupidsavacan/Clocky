# Phase 3A-2 production render / platform verification

Date: 2026-10-08. Branch: `feat/phase3a2-responsive-render`, based on merged #48/main
`7b7ab11bbd0efc38d733b14383d8591b62b60a20`.

**Status: moto/API34 verification completed and restored; API35 fails the400ms p95 target and reproduces an existing alarm boot crash on the PR APK. Phase 3A is not complete. Keep PR49 Draft.**
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
**not actual launcher acceptance**. The resumed moto and independent API35 launcher results below
provide the subsequent real-send evidence; no shipping bitmap claim is made from Robolectric alone.

## Build verification

Robolectric SDK35 requires JDK21; CI now selects21 while application bytecode remains17.
Native SDK/template coverage exceeded Gradle's default512MiB heap in the full suite; test forks
use2GiB and recycle after20 test classes. No production dependency added. Final full run and production-commit recheck: `testDebugUnitTest lintDebug assembleDebug` green;468 tests,0 failures/errors,
including existing model/codec regressions. Lint has existing warnings but no errors.
PR49 Current App CI on0aa4e5d passed (8m34s); subsequent commits only add this evidence.

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
Fresh placement was subsequently verified in the dedicated session below; existing widgets were preserved.

Committed production source (`b00956a`) recheck: session `s-20261008-085636-emulator-5562`
prepared/finished. Rebuilt APKsha256
`8495e09b5cb9b6caf69ce0ac1facde9ee8f40fd4fc36b909eb8ee05eb65e9eb1` installed in place:
IDs5/7 and settings byte-identical, no crash/ANR. Ticking11:56→11:57 in35.2s,6/6 process checks absent.
The APK hash changed after comment cleanup/recompile; this check covers the committed-source artifact.

- Portrait/landscape pair displayed correctly; original rotation settings restored.
- Candidate minResize110×40dp allowed2×1,2×2,3×1,3×2; restored4×2. Resize generated intermediate
  callbacks/classes during the gesture. API30 has no SizeF map host-selection behavior.
- `proc kill`, then `widget --ticking --no-process --index1 --timeout60`: visible time11:13→11:14,
  nine process checks absent. TextClock continues ticking in the host.
- Studio showed four selector buttons; scoped Square/Large Time weights absent; Info scope visible.
  Temporary draft changes discarded; final settings compare SAME.

Fresh placement/parity session `s-20261008-090311-emulator-5562` prepared/finished: a temporary
Clocky Default widget ID9 was placed on a separate launcher page at4×2, then resized through
4×3 Large,4×4 Square,4×1 Strip and4×2 Card. Quick Tune automatically selected the production
class, and Time/Date template, visibility, face IDs and geometry matched the placed widget after
uniform display scaling. Observed text rectangles (pixels; rounding differences expected):

| Class | Placed Time / Date | Preview Time / Date |
|---|---|---|
| Strip |468×230 /212×47|468×230 /212×47|
| Card |575×284 /265×58|465×230 /215×47|
| Square |575×284 /265×58|224×111 /104×23|
| Large |575×284 /265×58|303×149 /140×30|

This checks Clocky Default with Time/Date, not all templates or worst-case Info/font combinations.
The created widget was removed via the observed launcher Remove target; IDs5/7, settings and
metadata compare SAME to baseline, no crash/ANR. Existing widget positions were not changed.

Shared-path tests establish effective-spec/geometry parity for all four classes. API30's simple
Time/Date placed/Preview comparison now covers four classes; real worst-case Info/fallback
and the resumed moto/API35 stress parity is recorded below. Sample alarm text remains the existing editor-only
placeholder when no real alarm is available. Square/Large own weight editing is hidden; global
weights remain editable. Info scoped patches edit visibility/size/offsets/alignment; color/font/opacity remain global.
No forbidden Info scoped-weight field is introduced.

## Metadata decision

The submitted production metadata explicitly retains minResize250×40dp. Candidate110×40dp
passed the template gate, API30 and moto resize checks, including fresh-placement/picker and upgrade.
API35 performance/no-crash gates remain open; do not mix widening into an uncertified release.
API30 fresh placement/picker/final-build upgrade now pass. Default minWidth250dp/minHeight70dp,
targetCell4×2, provider component and schema2 remain unchanged. Do not widen release metadata
on this evidence alone.

## Enabled hosts / not exercised

| Host | Current ability | Remaining evidence |
|---|---|---|
| moto g13 API34 / Motorola launcher | owner unlocked; verified/restored/finished | home rotation deliberately not enabled; no isolated frozen-map experiment |
| Pixel API35 emulator5560 | parallel agent recovered, verified and restored launcher | p95 target failed; alarm boot crash reproduced on PR APK |
| Pixel API30 emulator5562 | prepared, verified and finished | worst-case Info/font four-class visual parity |
| API31–33 /API25 | not exercised | optional boundary/regression coverage |

No automated device unlock, launcher-setting change on moto, wipe, reboot, uninstall, clear, log reset or
force-stop was performed. Initial API30 operations used cdev; the moto binary screenshot exception is recorded below. Package-install-related lifecycle
force-stop lines in logs are system installation behavior, not an issued force-stop command.

Remaining steps now concern the API35 p95 failure and final restoration/comment described below.
CI green alone does not
close these gates. Phase3B/3C may start only after owner merge of completed3A.

## Resumed moto/API34 verification (2026-10-08)

Tested production source remains `b00956a`, PR HEAD `c5a14b7`. Independent managed worktree,
session `s-20261008-104435-ZY22GSDPFW`, prepared and finished. Motorola Launcher3,720×1600,
280dpi/density1.75,ja-JP,font-scale1. Screen mirroring used installed scrcpy4.1 with explicit
moto serial, control enabled and software renderer; PC click and Home key were verified.
Owner subsequently waived continued GUI use. Owner's PC went offline for six hours and reconnected;
that elapsed gap is not a device time-setting change issued by this task.

Actual pre-upgrade APK was pulled through cdev and hashed: `504294c7e5b494f8f23ae24909332b361fb4b66dcb9a9bb420c017aa65e11ee4`,
matching the recorded3A-1 moto artifact. Initial stale install-cache output from a prior cdev
session was not used as baseline identity. In-place PR APK `8495e09b5cb9b6caf69ce0ac1facde9ee8f40fd4fc36b909eb8ee05eb65e9eb1`
preserved ID23 and byte-identical settings ([upgrade summary](moto-upgrade-summary.md)).
4×3 now classifies Large; R2 preserves the prior Card appearance: baseline and final Time rectangle
`[89,305][630,551]`, Date `[261,558][458,598]`, root `[28,103][692,801]` are identical.

### Real production timing and memory

Same queued generation/send path and ephemeral CENTER_STACK worst-case design as the API30 audit.
Nearest-rank p95,n30 per row; first = first harness update, not controlled cold start.
Preview/local apply/bitmap/Parcel inspection is after production timestamps and excluded.
Generation below is `RenderBatch.generationNanos`; send/end-to-end use the surrounding updater.

| Cell/class | First / p95 generation | First / p95 send | First / p95 end-to-end | Bitmap bytes | Parcel bytes |
|---|---:|---:|---:|---:|---:|
|4×1 Strip|291.97 /183.81ms|3.67 /2.08ms|297.79 /188.27ms|1,020,960|4,452|
|4×2 Card|317.21 /241.52ms|1.50 /2.00ms|322.46 /246.07ms|2,243,968|5,212|
|4×4 Square|304.33 /251.80ms|1.62 /2.01ms|308.59 /257.00ms|4,689,984|5,212|
|4×3 Large|538.63 /374.68ms|3.06 /2.69ms|545.48 /380.73ms|3,466,976|5,212|

Entry p95 resolve/fit/compose (ms), independently calculated; phase percentiles cannot be added:

| Common class | Actual host SizeF key | Resolve | Fit | Compose |
|---|---|---:|---:|---:|
|Strip|363.42856×122.28571|0.90|113.92|4.75|
|Strip|667.4286×58.285713|1.10|60.89|3.48|
|Card|363.42856×260.57144|0.91|131.35|14.43|
|Card|667.4286×132.57143|2.54|82.66|5.97|
|Square|363.42856×537.1429|1.09|142.44|12.01|
|Square|667.4286×281.14285|1.36|67.05|11.93|
|Large|363.42856×398.85715|2.78|219.92|10.21|
|Large|667.4286×206.85715|1.79|71.74|8.64|

All entries within a batch use the same production class and effective system-sans face.
Map-construction timing is retained in local JSON, separately from per-entry composition.
4×4 actual gradient send/display succeeded for all30 samples;4,689,984B is32.15% below the
6,912,000B screen-derived moto limit. No allocation/transaction-too-large exception observed.
No image-quality reduction. The first package-update generation after installing the metadata
candidate logged2,204.80ms (fit1,340.08/308.90ms,resolve82.81/2.22ms,compose1.46/0.85ms).
This and Large's538.63ms first harness sample expose cold/inflation/sampling latency; normal
four-class p95 is below400ms. Environment/font probing and inflation warming are profiling
candidates, not proven causes from these phase timestamps. Host resource load was uncontrolled.

### Resize, parity, metadata and ticking

4×1 Strip /4×2 Card /4×3 Large /4×4 Square passed real display checks. Production PreviewHost
rendered the same ephemeral design with actual host entry size; resolved-spec equality was true
for all four classes. Screens show matching Time/Date/UTC Info, seconds, localized午後 marker,
alignment/gap/padding, strong shadow, effective fallback and fit without clipping/overlap.
Strip hides Info in both paths. Preview frame and launcher actual inner bounds differ slightly
from reported SizeF because of host padding; no pixel-identity claim is made.
Quick Tune showed all four selectors and selected the actual Square class at4×4.
The debug audit now accepts `--ez hold true --ez preview true` and optional `--es template SPLIT`;
this reuses production PreviewHost, never saves a design, and Back/onDestroy restores the saved
design. Exit the held audit before process-kill tests. No additional renderer was introduced.

Held-drag proof: DOWN at the bottom resize handle, MOVE to3×1, capture log/screen **before UP**.
At17:26:51.595 the updater already sent the Strip map with keys268.57144×122.28571 and
496.57144×58.285713. Final settled class/key/appearance matched. No final stale layout, blank or
Clocky crash observed. Runtime publication was not deliberately reordered; adversarial stale
protection remains established by the queue tests. Existing-entry selection was not isolated by
freezing regeneration. No moto launcher setting was changed to enable home rotation; the API35
parallel check supplies the enabled-host rotation evidence.

Candidate metadata APK `10d71075ab56f79df6be1a0340f72fc650b58974282e0062cee891cef2a28722`
temporarily used110×40dp.2×1,2×2,3×1,3×2 all reachable; natural geometry fit and display stable.
Picker remained4×2; fresh Clocky Default ID24 appeared at4×2, then only that temporary widget
was removed using the observed launcher Remove target. SPLIT+2×1 Strip actual diagnostic apply,
send/display and Preview succeeded with173.71428×122.28571 /325.7143×58.285713 keys;
no clipping/overlap, matching production spec. Original300-case/native fractional audits remain
zero failures. This is additional real SPLIT evidence, not a claim of exhaustive real-launcher
template×cell coverage. Final release metadata is again250×40dp,default minWidth250,target4×2.

Original ID23 returned to its original4×3 cell/root/geometry. Intermediate audit-UI APK
`ac38bfadf19f943284c8913ffbf0a9db189c0e547156a1b352fb679c3cdbdb6c` was used for the following ticking proof.
`proc kill` then `widget --ticking --no-process` proved17:30→17:31 in64.8s,9/9 process-absent
checks. Final settings byte-identical and IDs SAME[23]; no Clocky crash/ANR in retained final
evidence. System rotation restored to auto/user0,
temporary log.tag.ClockyRender restored to empty, session finished. Raw captures remain local.

Final audit-lifecycle guard/status follow-up APK
`099caa47396e9e542d4a30bffeeb863009f3182a318cd28c5f3ac841c2799402` installed in place in
session `s-20261008-174210-ZY22GSDPFW`, which was prepared/finished. A100-sample held audit was
exited with Back while running: no resumed Preview/continuing audit, original rendering restored,
no Clocky crash/ANR, IDs SAME[23]/settings byte-identical to the original3A-1 baseline
([final summary](moto-final-summary.md)). This final debug APK remains installed; release metadata
250×40dp and production render source unchanged.

### Owner ADB exception and limitations

cdev was tried first. Seconds-ON UI dumps failed idle detection; overlapping failed dump attempts
also produced UiAutomationService registration errors, not Clocky failures. Those attempts were
allowed to finish before further UI work. Binary PNG through cdev cmd_adb was corrupted because
it decodes stdout as text. Under the explicit PR49 owner exception, screenshot acquisition used
`adb -s ZY22GSDPFW exec-out screencap -p`, writing binary output directly to local files; commands
and reason are retained in local `moto-direct-adb-record.txt`. All other device commands, including
motionevent gestures, log-property temporary change/restoration, install/kill/ticking, were cdev.
No emulator/ADB-server operation, device authentication automation, clear/uninstall/force-stop,
launcher preference change or log reset was issued.

Full post-audit-source checks: testDebugUnitTest468 tests/0 failures/errors,lintDebug,assembleDebug
green (JDK21). No production classifier/schema/provider/domain/font/AMPM change in this resumption.

## API35 parallel report integration

The independent [API35 final PR report](https://github.com/stupidsavacan/Clocky/pull/49#issuecomment-6056052332)
was read and integrated on2026-10-08. Tested `c5a14b7`.
Functional Keying A, real4×4 send/memory, four-class stress parity, rotation, resize (including
rejected stale sends), upgrade/settings and process-absent ticking passed as reported.
Real4×4 allocation14,203,708B,Parcel8,056B,8.67% headroom; no quality change needed.
Required performance failed: provider total p95 Card427.57ms /Square671.30ms (batch424.08/665.63ms).
First batch1,988.17/1,535.25ms. Fit and bitmap/map construction are profiling candidates;
parallel host resource contention prevents attributing the entire excess to implementation.
After host disk space was recovered, the agent completed the final cold boot: services/launcher
running, settings byte-identical, ID2/original cell/span/pixel bounds restored, sessions finished.
That boot reproduced the pre-existing AOSP alarm PendingIntent mutability crash on the PR APK
at17:35:07 and17:35:30 (AlarmInitReceiver→fixAlarmInstances→showMissedNotification). This also
fails the required no-crash upgrade/restoration criterion. Source inspection confirms
AlarmNotifications.kt's missed-notification getService and AlarmStateManager.kt scheduling
still use FLAG_UPDATE_CURRENT without a mutability flag; these sites are unchanged by this PR.
Do not change the AOSP domain without an owner ruling (original stop condition10).

**Shipping decision: keep Draft; do not declare Phase3A complete or start3B/3C.** Recheck API35
performance under a stable environment, profile/optimize if still over400ms, and obtain an owner
ruling for the existing alarm boot failure. This moto-only task did not operate or restart that emulator.
