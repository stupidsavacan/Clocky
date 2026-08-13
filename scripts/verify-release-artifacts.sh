#!/usr/bin/env bash
set -euo pipefail

if (( $# == 0 )); then
  echo "Usage: $0 <artifact-file-or-directory> [...]" >&2
  exit 64
fi

readonly FORBIDDEN_PACKAGE="com.google.android.deskclock"
readonly HASH_GOOGLE_CLOCK_BASE="58fc4c98cfc80786d81a927fed6b87852b8367734f758523c8264be5a2ded21d"
readonly HASH_GOOGLE_CLOCK_XHDPI="65ca09d76f4e7b5dc174eee36be60c1670ae5578352df55fd2570b12fbad9191"

fail=0
count=0

check_file() {
  local file="$1"
  local hash=""
  local package_id=""

  [[ -f "$file" ]] || return 0
  ((count += 1))

  case "$file" in
    *reference_apk/*|*reference/vendor/*)
      echo "REJECT: reference path must not be exported: $file" >&2
      fail=1
      ;;
  esac

  hash="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$hash" == "$HASH_GOOGLE_CLOCK_BASE" || "$hash" == "$HASH_GOOGLE_CLOCK_XHDPI" ]]; then
    echo "REJECT: artifact matches a known Google Clock reference APK: $file" >&2
    fail=1
  fi

  if [[ "$file" == *.apk ]]; then
    if command -v apkanalyzer >/dev/null 2>&1; then
      package_id="$(apkanalyzer manifest application-id "$file" 2>/dev/null || true)"
      if [[ "$package_id" == "$FORBIDDEN_PACKAGE" ]]; then
        echo "REJECT: artifact package is $FORBIDDEN_PACKAGE: $file" >&2
        fail=1
      fi
    elif [[ "${REQUIRE_APKANALYZER:-0}" == "1" ]]; then
      echo "REJECT: apkanalyzer is required for release validation but was not found." >&2
      fail=1
    fi
  fi
}

for input in "$@"; do
  if [[ -d "$input" ]]; then
    while IFS= read -r -d '' file; do
      check_file "$file"
    done < <(find "$input" -type f \( -iname '*.apk' -o -iname '*.aab' -o -iname '*.apks' -o -iname '*.xapk' -o -iname '*.zip' \) -print0)
  else
    check_file "$input"
  fi
done

if (( count == 0 )); then
  echo "REJECT: no candidate artifacts were found." >&2
  exit 1
fi

if (( fail != 0 )); then
  exit 1
fi

echo "Artifact guard passed for $count file(s)."
