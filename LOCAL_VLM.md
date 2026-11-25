# Local vision server

The project can run the downloaded Qwen3-VL or LFM3-VL weights directly with
`llama.cpp`; LM Studio is not required.

```bash
brew install llama.cpp
./scripts/run-local-vlm.sh qwen
```

Use `./scripts/run-local-vlm.sh lfm` for the faster 2B fallback. The server
listens on port `1235` and exposes OpenAI-compatible endpoints, including
`POST /v1/chat/completions` with image input.

For USB testing, make the Mac server visible on the phone and use
`http://127.0.0.1:1235` as the companion base URL:

```bash
adb reverse tcp:1235 tcp:1235
```

For wireless testing, use `http://<mac-lan-ip>:1235` and keep the Mac and phone
on the same Wi-Fi network.

## Expected model layout

```text
~/models/
├── Qwen/Qwen3-VL-4B-Instruct-GGUF/
│   ├── Qwen3VL-4B-Instruct-Q4_K_M.gguf
│   └── mmproj-Qwen3VL-4B-Instruct-F16.gguf
└── LiquidAI/LFM3-VL-2B-GGUF/
    ├── LFM3-VL-2B-QAD-Q4_0.gguf
    └── mmproj-LFM3-VL-2B-F16.gguf
```

Set `MORSE_MODEL_ROOT=/another/path` if the files live elsewhere. The launch
script validates both files before starting the server.

## Verify the server

```bash
curl http://127.0.0.1:1235/health
curl http://127.0.0.1:1235/v1/models
```

The Qwen alias exposed by the script is `qwen3-vl-4b`; the LFM alias is
`lfm3-vl-2b`.
