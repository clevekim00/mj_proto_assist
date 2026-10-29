#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
(cd ui && npm ci && npm run build && npm test)
./gradlew test :local-api:installDist :cli:installDist
python3 scripts/smoke.py
