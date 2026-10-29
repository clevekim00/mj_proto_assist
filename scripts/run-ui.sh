#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
(cd ui && npm ci && npm run build)
./gradlew :local-api:installDist
exec local-api/build/install/local-api/bin/local-api
