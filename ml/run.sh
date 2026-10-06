#!/usr/bin/env bash
set -euo pipefail
# Override these two paths on another machine; the source code has no hidden data inputs.
AI_DATA_DIR=${AI_DATA_DIR:-$PWD/ml/data}
AI_PYTHON=${AI_PYTHON:-python3}
export KERAS_HOME="$AI_DATA_DIR/keras-cache"
export MPLCONFIGDIR="$AI_DATA_DIR/matplotlib-cache"
"$AI_PYTHON" ml/prepare_data.py --data-dir "$AI_DATA_DIR"
"$AI_PYTHON" -m pytest ml/tests -q
"$AI_PYTHON" ml/train.py --data-dir "$AI_DATA_DIR" --output "$AI_DATA_DIR/run-20261006"
