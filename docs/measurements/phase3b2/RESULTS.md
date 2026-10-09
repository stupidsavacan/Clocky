# Phase 3B-2: pinch and preview widget resizing

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
