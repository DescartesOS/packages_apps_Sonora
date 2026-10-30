#!/usr/bin/env bash
# Regenerate the release APK imported by the Sonora Soong module.
set -euo pipefail
cd "$(dirname "$0")/.."
git submodule update --init --recursive
./gradlew :app:assembleFossMobileUniversalRelease --console=plain "$@"
mapfile -t sonora_apks < <(find app/build/outputs/apk/fossMobileUniversal/release -maxdepth 1 -type f -name '*.apk')
if (( ${#sonora_apks[@]} != 1 )); then
    echo 'Expected one release APK; remove stale APK outputs and rebuild.' >&2
    exit 1
fi
mkdir -p prebuilt
cp "${sonora_apks[0]}" prebuilt/Sonora.apk
