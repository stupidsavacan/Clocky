# Platform API static scan

This is a pre-build static scan. It intentionally over-reports suspicious platform coupling; compiler/API-lint validation comes later.

- Source files scanned: **160**
- Suspicious references: **2**

## Summary

| Kind | Count |
|---|---:|
| hidden annotation | 2 |

## Findings

| Kind | File | Line | Match |
|---|---|---:|---|
| hidden annotation | `app/src/main/java/com/android/deskclock/Utils.kt` | 506 | `@hide` |
| hidden annotation | `app/src/main/java/com/android/deskclock/AsyncRingtonePlayer.kt` | 57 | `@hide` |
