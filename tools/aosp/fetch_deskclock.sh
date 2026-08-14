#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/deskclock-upstream.env"

OUT_ROOT="${1:-${REPO_ROOT}/vendor-staging/aosp-deskclock}"
ARCHIVE="${OUT_ROOT}/deskclock-${AOSP_DESKCLOCK_TAG}.tar.gz"
SOURCE_DIR="${OUT_ROOT}/source"

mkdir -p "${OUT_ROOT}"
rm -rf "${SOURCE_DIR}"
mkdir -p "${SOURCE_DIR}"

printf 'Fetching AOSP DeskClock %s\n' "${AOSP_DESKCLOCK_TAG}"
printf 'Expected commit: %s\n' "${AOSP_DESKCLOCK_COMMIT}"

if command -v curl >/dev/null 2>&1; then
  curl --fail --location --retry 3 --output "${ARCHIVE}" "${AOSP_DESKCLOCK_ARCHIVE_URL}"
elif command -v wget >/dev/null 2>&1; then
  wget --tries=3 --output-document="${ARCHIVE}" "${AOSP_DESKCLOCK_ARCHIVE_URL}"
else
  echo 'Neither curl nor wget is available.' >&2
  exit 2
fi

tar -xzf "${ARCHIVE}" -C "${SOURCE_DIR}"

required=(Android.bp AndroidManifest.xml src res)
for entry in "${required[@]}"; do
  if [[ ! -e "${SOURCE_DIR}/${entry}" ]]; then
    echo "Archive validation failed: missing ${entry}" >&2
    exit 3
  fi
done

cat > "${OUT_ROOT}/UPSTREAM.txt" <<EOF
project=platform/packages/apps/DeskClock
tag=${AOSP_DESKCLOCK_TAG}
commit=${AOSP_DESKCLOCK_COMMIT}
archive_url=${AOSP_DESKCLOCK_ARCHIVE_URL}
EOF

printf 'DeskClock source staged at: %s\n' "${SOURCE_DIR}"
printf 'No APK or Android build was performed.\n'
