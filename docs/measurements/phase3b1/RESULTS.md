# Phase 3B-1 Canvas — moto g13 verification summary

Host: moto g13 / Android 14 (API 34) / Motorola Launcher3 / ja-JP, widget id 23 (existing 3A-2 widget).
Tested code: branch `feat/phase3b1-canvas`, debug APK sha256 `cbeab591e282701d48d91d274be215832eaaca98c905b582be90e7c9f4d96ad0`
(built from the sources of commit `5d9a56b`; two earlier APKs of the same branch exposed the overlay bugs listed below).
Session: cdev `s-20261008-230744-ZY22GSDPFW` (finished). Raw evidence stays local (git-ignored).
This is 3B-1 evidence only; it is not evidence for Agent B / 3C-1.

| Check | Result |
|---|---|
| Selection frame vs applied view | Frame hugs the Time text in the real Preview (after fix, see below) |
| Single-finger drag (`cdev drag`) | Time moves in Preview with the finger; one drag = one Undo step; Undo removes it, Redo is enabled |
| Snap | A drag ended with saved `time.yDp = 51.428592681884766` where the raw finger path gave ~40 dp: the Y value is an exact snap-line value (X, off every line, stayed arbitrary: `80.44178009033203`). Haptic itself is not observable over adb |
| Save → placed widget | After Save the Launcher3 widget showed the same shifted Time (same clipping at the card edge as the Preview); settings diff vs baseline was exactly `time.xDp` and `time.yDp` |
| 1 dp nudge | Three taps on Date → `日付 の横位置` = 3 dp; long press repeats and the slider refreshes after release |
| Cancel | Back → "discard" after nudging left saved settings untouched |
| Restore | Layout reset + re-selecting "Time first" for the Card scope returned `clocky_widget_settings.xml` to **byte-identical** with the pre-test baseline (`settings SAME`, widget ids `[23]`) |
| Ticking / crash | process-absent ticking ok (23:18 → 23:19, 0/8 samples with a Clocky process); CRASH none, ANR none |

Bugs found by this device run (fixed before the final APK, regression test added):
1. A `MATCH_PARENT` overlay under the `wrap_content` preview panel inflated the panel to the full available height (Studio controls pushed off screen).
2. After the first fix the overlay stayed 0 px tall because a sibling is not re-measured when the preview frame is resized after render; it now measures from the preview frame and is re-measured by `PreviewHost.onFitted`.
3. The selection frame drew stale geometry until the post-render scale was applied; it is now invalidated by the same callback.
Robolectric had not caught 1–2 because its layout used exact sizes.

Not verified on device (cannot be observed with cdev / out of scope): haptic feedback, two-finger snap cancel
(a second pointer cannot be injected; covered by Robolectric MotionEvent tests only), TalkBack, API < 31 behaviour
(Robolectric SDK 30 only), landscape Studio, RTL on device, other launchers/APIs (Phase 5+).
Installed APK left on the device: the 3B-1 branch build above (not the previous baseline APK).
