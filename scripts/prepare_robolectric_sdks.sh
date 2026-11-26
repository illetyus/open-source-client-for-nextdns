#!/usr/bin/env bash
set -euo pipefail

manifest="$(dirname "${BASH_SOURCE[0]}")/robolectric-sdks.sha256"
sdk_dir="${1:?Usage: prepare_robolectric_sdks.sh SDK_DIRECTORY}"
mkdir -p -- "$sdk_dir"

repositories=(
  https://repo.maven.apache.org/maven2
  https://maven-central.storage-download.googleapis.com/maven2
)
matches_sha256() {
  printf '%s  %s\n' "$1" "$2" | sha256sum --check --status
}

while read -r checksum jar; do
  destination="$sdk_dir/$jar"
  if matches_sha256 "$checksum" "$destination" 2>/dev/null; then
    printf 'Verified cached %s\n' "$jar"
    continue
  fi
  version="${jar#android-all-instrumented-}"
  coordinate="org/robolectric/android-all-instrumented/${version%.jar}/$jar"
  temporary="$(mktemp -- "$sdk_dir/.download-XXXXXXXX")"
  trap 'rm -f -- "$temporary"' EXIT
  for repository in "${repositories[@]}"; do
    if curl --fail --silent --show-error --location --proto '=https' --retry 2 \
        --retry-delay 2 --connect-timeout 12 --max-time 120 --output "$temporary" "$repository/$coordinate" &&
        matches_sha256 "$checksum" "$temporary"; then
      mv -f -- "$temporary" "$destination"
      break
    fi
  done
  rm -f -- "$temporary"
  trap - EXIT
  if ! matches_sha256 "$checksum" "$destination" 2>/dev/null; then
    printf 'Unable to retrieve verified Robolectric SDK %s\n' "$jar" >&2
    exit 1
  fi
  printf 'Verified downloaded %s\n' "$jar"
done < "$manifest"
