# Issue54 — transient preview enlargement during edits

Host: moto g13 / API34 / Motorola Launcher3 / ja-JP, density1.75, existing widget23.
Work based on PR53 head50a09b1936eee5322ac37f593df4b9806aabd826; PR52/My Designs is not included.
Owner explicitly transferred the Issue51 device lease to this task before any device access.
Session: `s-20261008-233651-ZY22GSDPFW`. Final restoration/build status is recorded below.

## Reproduction and affected area

Actual installed baseline APK was pulled with cdev and hashed:
`cbeab591e282701d48d91d274be215832eaaca98c905b582be90e7c9f4d96ad0`,
matching PR53's tested source5d9a56b (50a09b1 adds documentation).
Studio was already open; Large preview showed an orange background, Time and Date.

Recorded screen video through `cdev adb -- shell screenrecord --time-limit25
/data/local/tmp/clocky-dev-issue54-*.mp4`, then cdev pull. Native input swipes changed size,
weight and opacity separately and dragged the selected Time. Each capture includes the preview,
class selector, other text and controls before/during/after the operation. Video is variable-rate;
frame counts are observations from decoded frames, not refresh-rate or frequency estimates.

Reproduction is **the entire applied RemoteViews preview child**, including its background and text.
The surrounding panel `[28,182][692,521]`, top bar and selector remain stable. The orange card's
visible width changes270→446px, with the larger drawing clipped at the panel edge.446px is a
clipped visible width, not the full render size or an enlargement ratio. Example size frames26–102
at18.141–19.331s and weight frames183–239 at20.794–21.571s show the enlarged child; after the
operation it returns to its ordinary scale. Opacity-only changes show the same background jump,
which cannot be explained by a font-width/fit change. Movement also retriggers this render path.

Saved widget settings stayed byte-identical after discarding the pre-fix edits. No placed-widget
magnification was observed. This bug's scale assignments are editor-only; the production
resolve/fit/composer is unchanged. This does not claim arbitrary fit-driven text changes are bugs.

## Root cause and fix

`DesignPreview.render()` replaces the applied child on each edit. A new View starts at scale1.
`PreviewHost.fitIntoParent()` previously applied the presentation scale only inside `frame.post`.
During continuous edits, the new unscaled child could be drawn before that queued fit; more
replacements could also leave queued fits targeting detached children. Static settled screenshots
miss this intermediate state. The before-fix paused-looper test reproduced expected0.3846154,
actual1.0 immediately after render.

Apply the same parent-width/maximum-height scale and frame dimensions synchronously with render.
Retain a deferred fit for initial parent measurement, guarded by current-child identity. Unattached
preview callers receive default frame layout params; existing SDK23/28/30/31/34/35 parity tests
cover that case. No cached user size, hidden content, alternate renderer, fit adjustment, codec,
provider, widget options or saved design change is introduced. Scale still never exceeds1.

## After-fix observation

First fixed APK SHA256:
`7b41f1490357cf74e3fd2e861e58288bb5b3ea724f3e7739268cb14dad94459d`.
This implements synchronous scale/current-child guard. The subsequent final source adds only
missing-layout-params handling for unattached PreviewHost callers, not the attached Studio path.

[Derived frame summary](frame-summary.json) counts **every decoded frame**, with a fixed color mask
on the orange card in the same preview ROI. Contact sheets across time and enlarged runs were also
visually inspected locally. The mask is supporting evidence for the unchanged background, not a
complete geometry/overlap detector. Source videos, images, settings and raw logs stay ignored/local;
no private video is uploaded.

| Capture | Decoded frames | Card visible widths | Enlarged frames (>300px) |
|---|---:|---|---:|
| Before: size / weight / move |287|270 or446px|147|
| Before: opacity / move |168|270 or446px|81|
| After: size / move / Undo |288|270px|0|
| After: opacity / weight / Undo |170|270px|0|
| After: independent weight / Undo |195|270px|0|

The independent weight capture visibly changes900→490, including fallback-note panel rebuilds,
then Undo returns900. The other after captures show size120→174→Undo120, Time moving and undoing,
and opacity100→52→Undo100 without the card jumping. Intentional text size/weight/fitting changes
remain visible. Four-class selector switching was exercised and returned to Large.

Save/reopen check: numeric size120→124, Save, placed widget, collect. Exactly one settings path
changed (`widget.23.settings.time.sizeSp`); ID23 retained, no crash/ANR. Placed Time becomes
`[80,301][639,555]` and Date `[261,562][458,602]`, consistent with the intended larger text. Reopen shows124; set120 and
Save restores the entire settings file byte-identically to the pre-test baseline. Restored placed
Time/Date bounds `[89,305][630,551]` / `[261,558][458,598]` match the previous3A/3B baseline.
The cdev class estimate changes between an Activity baseline and Launcher collection; that is a
context/tool estimate, not evidence of a widget options/class change.

## Automated verification and limitations

New paused-looper regression covers all four edit types in all four classes before and after
queued work, plus superseded-child fitting/late parent-width measurement. Existing CanvasStudio
and StudioActivity tests cover drag, Undo/Redo, discard, Save and stored-value semantics. The
pre-fix regression fails; synchronous scaling passes it. The first full suite exposed null layout
params in unattached previews; that is fixed before final verification.

No other-device/emulator operations, uninstall, clear, force-stop, launcher preference changes or
raw ADB were used. No manual multi-finger/haptic, other-launcher, landscape/RTL or exhaustive
per-slot visual matrix is claimed. A screen recording samples the actual screen, not every host
compositor frame; automated immediate-state checks complement it. No production performance
budget is relaxed. PR53 and PR52 are not merged by this task.

## Final verification / restoration

Process-absent ticking after restoration:23:53→23:54 in18.2s,3/3 samples without a Clocky process.
Final `testDebugUnitTest lintDebug assembleDebug` successful (JDK21,9m10s):502 tests,
0 failures/errors/skips. Existing multi-SDK Preview/widget parity tests pass. Gradle used one
worker/768MiB daemon and in-process Kotlin compilation locally after an initial daemon exit;
no project build/resource policy changed.

Final retained APK SHA256:
`0f3e3bdb7353b02d6aed3164ad2d38c12253d5a180e687e704b5354c87658d27`.
Installed in place2026-10-09 00:02:41; final size-drag/Undo video (193 decoded frames) was inspected,
with no preview magnification. Exit to launcher; final collect settings byte-identical, ID23 retained,
no crash/ANR in collected evidence. cdev finish closed the session, removed all six recording temp
files; status has no PENDING RESTORE and original auto-rotation/font-scale/launcher remain.
The fixed PR53-based APK remains installed for the owner's use; it does not include PR52.
[PR-safe final cdev summary](moto-final-summary.md). Its main-log rollover warning limits the
retrospective log coverage; earlier baseline/save/restore collections also checked crash/ANR.
Raw frames, private settings and recordings remain local. No merge was performed.
