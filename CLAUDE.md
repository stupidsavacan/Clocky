# Clocky — notes for Claude Code

- Device/emulator verification: use `python tools/device/cdev.py <command>` (details: `tools/device/README.md`). Raw adb only via `cdev.py adb -- ...` (adds serial + safety guard).
- Start with `prepare`, end with `finish`. If `status` shows `PENDING RESTORE`, run `restore`.
- Exit 2/3 mean a decision is needed: never guess `--index` or `--serial`; show the candidates or `inspect`.
- Use `python`, not `python3`. From Git Bash pass device paths with `MSYS_NO_PATHCONV=1`.
- Evidence lives in `build/device/sessions/...` (git-ignored). Only `summary.md` may be pasted into PRs/issues.
- Never uninstall, `pm clear`, or `logcat -c` (widgets and settings would be lost).
- Unit tests: `python -m unittest discover -s tools/device/tests -t tools/device`.
