#!/usr/bin/env bash
# Compatibility alias; maintained tests have one runner.
set -euo pipefail
exec bash "$(dirname "$0")/run-current.sh"
