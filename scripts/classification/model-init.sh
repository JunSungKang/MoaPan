#!/bin/sh
set -eu
model=/models/qwen3.5-4b-q4.gguf
sha=00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4
verify() { printf '%s  %s\n' "$sha" "$1" | sha256sum -c -; }
if [ -f "$model" ]; then verify "$model"; exit; fi
# Only one initialiser may write this volume; interrupted files are never activated.
exec 9>/models/.init.lock
flock -n 9 || { echo 'Another model initializer is running'; exit 1; }
curl --fail --location --proto '=https' --tlsv1.2 --retry 2 --connect-timeout 15 --max-time 1800 -o "$model.part" https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf
verify "$model.part"
mv "$model.part" "$model"
chmod 0444 "$model"
