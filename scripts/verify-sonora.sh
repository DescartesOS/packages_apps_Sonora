#!/usr/bin/env bash
# Build and verify the same universal mobile variant used for phone testing.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew \
  :app:assembleFossMobileUniversalDebug \
  :app:testFossMobileUniversalDebugUnitTest \
  :app:lintFossMobileUniversalDebug \
  --continue --console=plain "$@"
