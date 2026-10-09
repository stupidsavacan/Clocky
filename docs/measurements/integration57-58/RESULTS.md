# PR #57 / #58 integration

Integration work dated 2026-10-09, isolated from the owner checkout and both feature worktrees.

## Inputs and scope

- Common base: `fe92f05558b4febb534262b260390fe4969d7893` (Broker v2, PR #56).
- PR #57: `c724bc7e2005308df7d2f5f32aa8e58043e0bb43`, including the exact MotionEvent float-precision regression fix.
- PR #58: `1ca93fd26422d5bb6bf167197d21f6f7bc657fd2` (My Designs import/export/share).
- Integration branch: `codex/integrate-57-58`. Both inputs merged without conflicts. Only the responsive-canvas architecture document overlaps; its 3B-2 and 3C-2 implementation records both remain present.
- No production adaptation was needed. Canvas uses the existing DesignEdits/EditSession model and production PreviewHost/RemoteViews; exchange uses the existing schema-2 codec and My Designs repository.
- Original PR branches and main are unchanged. This record does not authorize merging either PR or releasing the combined feature set.

## Integration regression

`CanvasStudioTest.classScopedPinchesRoundTripThroughFileAndTextExchangeWithoutChangingWidget` drives real StudioActivity MotionEvents after pseudo-resizing into Strip and Square classes. Each pinch must exactly equal DesignEdits applied to the measured pointer span ratio. The resulting two class patches must survive repository save, `.clocky` export/import, and `CLOCKY2:` export/import with exact whole-design equality.

Both imports create new IDs, retain the original My Design, and leave the installed widget design/options unchanged. Undoing both gestures restores the Studio draft while retaining the saved My Design. This connects the two features at their shared persisted design boundary; it does not simulate a real SAF picker or share recipient.

## Verification

- Targeted integration regression: passed on Windows/JDK 21.
- Full unit/Robolectric suite: 621 tests, 620 passed, 1 skipped, 0 failures/errors on Windows/JDK 21. The only skip is the known POSIX-only FileProvider root test below.
- `testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL (7m 30s). Lint reports 0 errors (1570 warnings and 1 informational finding).
- `git diff --check`: passed. No tracked APK/APKS/XAPK/AAB files; `bash -n scripts/verify-release-artifacts.sh`: passed (the Reference APK Guard checks).
- At record creation, combined Ubuntu Actions had not yet run; individual PR CI was successful at the input SHAs. Check the published integration SHA's workflow results before merging. CI links/results are reported with the final delivery.

## Device gates

No phone access performed. Owner has verified Broker v2 bootstrap (FREE, not DIRTY); this is separate from feature acceptance. The long-lived owner scrcpy window and existing widget ID23 are untouched.

Still pending: genuine two-pointer pinch, drag-to-pinch/cancel behavior, preview-to-installed-widget Save parity, performance, landscape touch, TalkBack/haptics, real SAF pickers and share targets. Any future phone check must use a broker FIFO lease and verified preservation/restoration. A one-finger cdev drag cannot validate pinch. Simulated five-column/unmeasured cell sizes remain extrapolations.

Windows skips PR #58's real FileProvider root test because JVM paths use backslashes; its declared roots are checked everywhere. POSIX execution of that test remains necessary for the combined branch.
