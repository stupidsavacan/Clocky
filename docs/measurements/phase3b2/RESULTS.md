# Phase 3B-2: pinch and preview widget resizing

**Latest status (2026-10-09):** #57 and #58 were owner-authorized and merged. Subsequent moto runs
verified native two-pointer interactions, handle dragging, drag-to-pinch history, reversible Save and
production generation timing. See [current moto evidence and remaining gates](MOTO_2026_10_09.md).
Older PENDING/no-phone-access statements below describe the implementation/CI-fix stages.
The historical instrumentation runner was disabled after Android's automatic target force-stop was
observed; that method must not be repeated under the current preservation rules. Final stock-main
APK, original widget ID23/settings/geometry and FREE Broker state were independently verified.

Implementation branch: `feat/phase3b2-pinch`, based on merged main `fe92f05558b4febb534262b260390fe4969d7893` (#56).
Scope: Studio input and preview sizing only. No provider identity, schema, resolver, installed widget options,
widget persistence format, Gallery/library, Import/Export, platform integration or Phase 5 changes.

## Behavior and implementation

- Tap production-applied Time/Date/Info to select. The first pointer retains the existing drag behavior on API31+.
  A second pointer disables snap immediately and for the remaining gesture. Moving both pointers together
  keeps unsnapped dragging; changing their span beyond touch slop starts pinch. Once pinching, offsets freeze.
  Size uses requested start sp multiplied by current/start span, then the existing `DesignEdits.setSize` clamp.
  Presentation scale, fit and device font scale never feed back into the requested size.
- Stable pointer IDs survive index reordering. Extra pointers do not replace the chosen pair. Lifting either
  active pinch pointer commits and ends the transaction; the remaining pointer cannot resume dragging.
  Lifting the second pointer before pinch leaves unsnapped drag available. Missing pointer IDs cancel.
- Drag and its pinch transition use one EditSession key and one undo entry. Scope is captured at down.
  ACTION_CANCEL, detachment and saving Activity instance state restore the pre-gesture design and full
  undo/redo checkpoint, including cancellation after no-op movement or at the history limit.
- API23-30 supports selection and pinch, with offsets still disabled. Text panels retain numeric size entry
  and sliders as the accessibility alternative; the overlay remains excluded from accessibility.
  Updated English/Japanese hints explain the input and numeric alternative. Spoken TalkBack output is untested.
- A 48dp bottom/end handle drags the preview between 2x1 and 5x4, snapping to whole cells. A tap or accessibility
  click opens a native cell-choice dialog. The spoken description and preview hint show cells and class.
  Class changes update the class buttons, text panels and `Only for <class>` scope. Class buttons exit simulation.
  Simulation survives Activity recreation/process-state restoration; it creates no design edit or undo step.
  Cancellation restores the previous simulation and class.
- The size pairs come from moto Launcher3 measurements in `phase3a0/raw-inputs.csv`: widths 173/268/363 dp
  and 325/496/667 dp; heights 58/132/206/281 dp and 122/260/398/537 dp. The nine measured cell combinations
  are reproduced exactly. Unmeasured combinations (including five columns) are extrapolated using measured
  increments; they are simulations, not evidence of actual launcher reachability. Start cells are chosen
  nearest the existing preview SizeContext. Landscape uses the wide/short measured entry and drag increments; RTL expands outward from the end edge.
- Class uses the existing `SizeClassRule` on maxWidth/minHeight, independent of orientation. 4x4 remains Square.
  `PreviewHost` receives an optional runtime-only SizeContext and uses production resolve/fit/RemoteViews.
  Installed widget size/options and settings are never written by pseudo-resizing; Save writes only the design.
- Issue54 synchronous presentation scaling and stale-child guarding are retained. Paused-looper tests inspect
  every simulated size and successive text-size renders before queued fits, then verify the final child.
  These are view-state assertions, not a real-device continuous-frame recording or launcher parity evidence.

## Automated verification

- Final targeted command: `gradlew.bat testDebugUnitTest --tests '*Canvas*' --tests '*EditSessionTest'
  --tests '*PreviewScaleTest' lintDebug assembleDebug`: **67 tests, 0 failures/errors/skips; lint and assemble successful**.
- Final full command: `gradlew.bat testDebugUnitTest`: **551 tests, 0 failures/errors/skips; successful**.
- `git diff --check`: passed.
- The first full run executed 544 tests with one failure in the new Info test's exact 18sp assertion.
  Actual requested size was 17.999998sp because the MotionEvent center had fractional pixels. The test now
  computes the expected size from the actual injected spans, retaining structural numeric-edit equality.
  It passed in the final targeted run. Initial failure XML/logs are preserved locally under `build/phase3b2`.
  No real-device results are inferred from either run.

Coverage includes pure pinch mapping and validation, all targets/scopes and clamping, transaction cancellation,
measured size pairs/class boundaries, grid bounds; Robolectric real MotionEvents on Studio/production applied
views (stable/reordered IDs, extra/lost pointers, lifting, drag transition, cancellation, shrinking, separate
undo steps, Time/Date/Info, class patch, API23/30), accessible cell selection, handle cancel, unchanged storage
and host options, Save, recreation, landscape entries and Issue54 intermediate rendering states.
Existing full unit tests also cover Studio controls, codecs, responsive provider rendering and library behavior.

## Moto and acceptance status

**PENDING, no phone access performed.** Offline inspection on this Windows PC found the default Broker v2
registry version 2 with an empty `devices` map and no enabled marker. Initial bootstrap is not verified.
No adb, cdev device command, scrcpy/projected window, install, widget edit/delete or device UI was used.
The old Issue51/3B-1/Issue54 sessions are historical evidence and were not treated as a lease.

Pending owner/device checks after verified bootstrap, via a finite broker FIFO run/hold lease with baseline
and restoration: genuine two-pointer pinch/spread and snap-disable transition, cancellation, preview/placed
widget parity after Save, repeated pinch/resize performance, portrait/landscape touch layout, TalkBack and
haptics. Single-pointer cdev drag cannot validate pinch. Existing widget IDs/settings must be preserved;
never uninstall, clear data or delete existing widgets. No other-host certification is claimed (Phase 5+).

## Risks and limits

Real-device shipping gate remains pending. Rapid production RemoteViews regeneration may cause jank;
no phone performance claim is made. Pinch is deliberately distinguished from unsnapped drag by span slop;
unequal finger movement can therefore transition into pinch. The pseudo handle represents simulated cells
and cannot predict a launcher's exact host padding, reachable cells or orientation behavior beyond recorded
data. The accessible numeric/dialog alternatives are covered in automation, not by spoken TalkBack checks.
No merge is authorized by these results.


## CI follow-up: fractional MotionEvent coordinates (2026-10-09)

[Ubuntu failure run 37891340667](https://github.com/stupidsavacan/Clocky/actions/runs/37891340667)
executed 551 tests on source `ec1554659dc57063b6fa6ba3fa087b3f7302808e`; only
`CanvasStudioTest.pinchTracksIdsAndIsOneUndoStepWithNumericEquality` failed at the full-design assertion.
The downloaded JUnit artifact shows Time size expected 96.0sp, actual 95.99999sp, with all other fields equal.
The difference is exactly one float ULP at 96sp (0.00000762939453125sp).

The test ignored its helper's actual coordinate-span ratio and multiplied the requested 64sp by an idealized
1.5. Float `(x + distance) - x` does not necessarily equal distance. A deterministic Windows MotionEvent
reproduction uses `Math.nextDown(512f)` as the first pointer coordinate, placing the applied preview beneath it:
start span 100.00003051757812px, final span 150.00003051757812px, ratio 1.4999998807907104, size
95.99999237060547sp. Keeping the old 96sp expectation reproduced the same failure locally. Linux's exact
starting coordinate was not logged; no particular font/layout origin or timing race is claimed.

The helper now reads start/final spans from the actual MotionEvents, independently of production pointer-ID
lookup. Time, scoped Date and API23/30 expectations use that ratio, as Info already did. Full DigitalDesign
assertions, one history entry, exact Undo and exact Redo remain strict. The new fractional-coordinate regression
also asserts the exact `nextDown(1.5f)` ratio and `nextDown(96f)` requested size with zero tolerance. The
API23/30 check is strengthened from a size tolerance to exact full-design equality. No production code changes;
no rounding of requested sizes, relaxed model comparisons or alternate preview renderer.

Local CI-fix validation: targeted CanvasStudio/CanvasResize/PreviewScale tests **45 passed**; full
`testDebugUnitTest` **552 passed, 0 failures/errors/skips**; `lintDebug` and `assembleDebug` successful;
`git diff --check` passed. Remote CI results are tracked in the PR follow-up. Original Ubuntu artifact/log and
Windows failing reproduction are retained under ignored `build/phase3b2`.

**Updated device status:** Owner confirmed Broker v2 initial bootstrap complete and two independent worktrees'
FIFO preservation-only smoke units (Issue #51 audit). The empty-registry observation above describes the initial
implementation, not current availability. This CI fix performs no phone access and leaves the long-lived
`moto g13 - Clocky owner control` scrcpy window untouched. Feature-specific genuine pinch, Save parity,
performance, TalkBack, haptic and landscape checks remain PENDING; bootstrap smoke is not feature acceptance.
