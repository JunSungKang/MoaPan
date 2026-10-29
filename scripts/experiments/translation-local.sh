#!/bin/sh
# macOS Apple Silicon, workspace-contained model/runtime/cache. No paid API.
set -eu
cd "$(dirname "$0")/../.."
ROOT=$(pwd -P)
DIR="$ROOT/.local/translation"
mkdir -p "$DIR/models" "$DIR/runtime" "$DIR/cache" "$DIR/config" "$DIR/tmp"
chmod 700 "$DIR"
export LLAMA_CACHE="$DIR/cache" XDG_CONFIG_HOME="$DIR/config" TMPDIR="$DIR/tmp"
MODEL="$DIR/models/index-translate-2b-q8.gguf"
BIN="$DIR/runtime/llama-b11960/llama-server"
case "${1:-status}" in
  install)
    [ "$(uname -sm)" = 'Darwin arm64' ] || { echo 'Requires macOS arm64'; exit 1; }
    [ -f "$DIR/runtime.tar.gz" ] || curl -fL --retry 2 -o "$DIR/runtime.tar.gz" https://github.com/ggml-org/llama.cpp/releases/download/b11960/llama-b11960-bin-macos-arm64.tar.gz
    printf '%s  %s\n' 58e5a5e1d7c2bf66cedd9c29e0323f3e570660a70a1e2156d32eed08f4cc1e3c "$DIR/runtime.tar.gz" | shasum -a 256 -c -
    tar -xzf "$DIR/runtime.tar.gz" -C "$DIR/runtime"
    [ -f "$MODEL" ] || curl -fL --retry 2 -o "$MODEL" https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF/resolve/3a0eca6ce931d88d00614d427ab3ed8ff0743902/Index-Translate-2B.Q8_0.gguf
    printf '%s  %s\n' a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3 "$MODEL" | shasum -a 256 -c -
    [ -f "$DIR/api.key" ] || (umask 077; openssl rand -hex 32 > "$DIR/api.key")
    chmod 644 "$DIR/api.key" # parent directory 0700; container secret mount is read-only
    ;;
  run)
    [ -f "$DIR/api.key" ] && [ -x "$BIN" ] || { echo "Run install first"; exit 1; }
    exec "$BIN" -m "$MODEL" --alias moapan-translation --host 127.0.0.1 --port 11435 -c 4096 -np 1 -ngl 99 --no-webui --offline --no-cache-prompt --cache-ram 0 --api-key-file "$DIR/api.key" --chat-template-kwargs '{"enable_thinking":false}' --log-disable
    ;;
  start)
    if [ -f "$DIR/server.pid" ] && kill -0 "$(cat "$DIR/server.pid")" 2>/dev/null; then echo 'Already running'; exit 0; fi
    [ -f "$DIR/api.key" ] && [ -x "$BIN" ] || { echo 'Run install first'; exit 1; }
    printf '%s  %s\n' a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3 "$MODEL" | shasum -a 256 -c -
    node - "$ROOT" "$DIR" <<'NODE'
const fs=require('node:fs'), {spawn}=require('node:child_process');
const [root,dir]=process.argv.slice(2);
const out=fs.openSync(dir+'/server.log','a',0o600);
const child=spawn('/bin/sh',[root+'/scripts/experiments/translation-local.sh','run'],{cwd:root,detached:true,stdio:['ignore',out,out]});
fs.writeFileSync(dir+'/server.pid',String(child.pid));
child.unref();fs.closeSync(out);
NODE
    count=0
    until curl --fail --silent --max-time 1 http://127.0.0.1:11435/health >/dev/null; do
      kill -0 "$(cat "$DIR/server.pid")" 2>/dev/null || { echo 'Translation process failed'; exit 1; }
      count=$((count+1)); [ "$count" -lt 30 ] || { echo 'Translation readiness timed out'; exit 1; }
      sleep 1
    done
    echo 'Local translation ready on loopback.'
    ;;
  stop)
    if [ -f "$DIR/server.pid" ]; then
      PID=$(cat "$DIR/server.pid")
      case "$(ps -p "$PID" -o command= 2>/dev/null || true)" in "$BIN"*) kill "$PID" ;; '') : ;; *) echo 'PID mismatch; refusing to stop another process'; exit 1 ;; esac
      rm "$DIR/server.pid"
    fi
    ;;
  status) curl --fail --silent --max-time 3 http://127.0.0.1:11435/health ;;
  *) echo 'Usage: sh scripts/experiments/translation-local.sh install|run|start|status|stop'; exit 1 ;;
esac
