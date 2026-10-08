# Clocky — notes for Claude Code

- Current verification scope: [moto personal use / Phase 5+](docs/product/VERIFICATION_SCOPE.md). moto g13/API34/Motorola Launcher3 is the current acceptance host; other-device/API/launcher verification is optional Phase 5+ work and must not be resumed without an owner request. Keep automated checks, preserve failed evidence, and require explicit owner merge authorization.

- Device/emulator verification: use `python tools/device/cdev.py <command>` (details: `tools/device/README.md`). Raw adb only via `cdev.py adb -- ...` (adds serial + safety guard).
- Start with `prepare`, end with `finish`. If `status` shows `PENDING RESTORE`, run `restore`.
- Exit 2/3 mean a decision is needed: never guess `--index` or `--serial`; show the candidates or `inspect`.
- Use `python`, not `python3`. From Git Bash pass device paths with `MSYS_NO_PATHCONV=1`.
- Evidence lives in `build/device/sessions/...` (git-ignored). Only `summary.md` may be pasted into PRs/issues.
- Never uninstall, `pm clear`, or `logcat -c` (widgets and settings would be lost).
- Unit tests: `python -m unittest discover -s tools/device/tests -t tools/device`.
- Process kill / ticking / restore proof: use `cdev proc kill`, `cdev widget --ticking --no-process` and `cdev collect X --compare-to Y --expect-same settings` (no `adb -- shell pidof/am kill`, no hand-written compare scripts). Never `am force-stop`.
