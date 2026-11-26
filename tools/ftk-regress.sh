#!/bin/bash
# Runs FishTestKit suites against THIS checkout's built jars (build first). Only run suites not yet PASS in D:\Brain\FishModDungeons\Open.md.
# Usage: FT_ONLY=sb3 bash tools/ftk-regress.sh [versions...]
FMD_DIR="$(cd "$(dirname "$0")/.." && pwd)" exec bash /d/FishTestKit/tools/runall.sh "$@"
