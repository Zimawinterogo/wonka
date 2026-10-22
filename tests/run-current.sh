#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew clean build --offline --no-daemon
python3 tests/run_resources.py
