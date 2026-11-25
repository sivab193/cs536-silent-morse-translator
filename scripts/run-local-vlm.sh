#!/usr/bin/env bash
set -euo pipefail

MODEL_NAME="${1:-qwen}"
SERVER_PORT="${MORSE_VLM_PORT:-1235}"
MODEL_ROOT="${MORSE_MODEL_ROOT:-$HOME/models}"

case "$MODEL_NAME" in
  qwen)
    MODEL_FILE="$MODEL_ROOT/Qwen/Qwen3-VL-4B-Instruct-GGUF/Qwen3VL-4B-Instruct-Q4_K_M.gguf"
    PROJECTOR_FILE="$MODEL_ROOT/Qwen/Qwen3-VL-4B-Instruct-GGUF/mmproj-Qwen3VL-4B-Instruct-F16.gguf"
    MODEL_ALIAS="qwen3-vl-4b"
    ;;
  lfm)
    MODEL_FILE="$MODEL_ROOT/LiquidAI/LFM3-VL-2B-GGUF/LFM3-VL-2B-QAD-Q4_0.gguf"
    PROJECTOR_FILE="$MODEL_ROOT/LiquidAI/LFM3-VL-2B-GGUF/mmproj-LFM3-VL-2B-F16.gguf"
    MODEL_ALIAS="lfm3-vl-2b"
    ;;
  *)
    echo "Usage: $0 [qwen|lfm]" >&2
    exit 2
    ;;
esac

if ! command -v llama-server >/dev/null 2>&1; then
  echo "llama-server is missing. Install it with: brew install llama.cpp" >&2
  exit 1
fi

for required_file in "$MODEL_FILE" "$PROJECTOR_FILE"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Missing model component: $required_file" >&2
    exit 1
  fi
done

echo "Starting $MODEL_ALIAS at http://0.0.0.0:$SERVER_PORT"
exec llama-server \
  --model "$MODEL_FILE" \
  --mmproj "$PROJECTOR_FILE" \
  --alias "$MODEL_ALIAS" \
  --host 0.0.0.0 \
  --port "$SERVER_PORT" \
  --ctx-size 8192 \
  --n-gpu-layers 99 \
  --flash-attn on \
  --jinja
