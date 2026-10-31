#!/bin/sh
set -eu
threads=${CLASSIFICATION_THREADS:-2}
case "$threads" in ''|*[!0-9]*) echo 'Invalid classification thread count'; exit 1;; esac
[ "$threads" -ge 1 ] && [ "$threads" -le 64 ] || exit 1
[ -s /run/secrets/classification_key ] || { echo 'Classification key missing'; exit 1; }
printf '%s  %s\n' 00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4 /models/qwen3.5-4b-q4.gguf | sha256sum -c -
# No outgoing network, web UI, RPC, prompt logs, GPU or dynamic model download.
llama-server -m /models/qwen3.5-4b-q4.gguf --alias moapan-classification --host 0.0.0.0 --port 11436 -c 4096 -b 64 -ub 64 -np 1 -ngl 0 -t "$threads" --no-webui --offline --no-cache-prompt --cache-ram 0 --api-key-file /run/secrets/classification_key --reasoning off --chat-template-kwargs '{"enable_thinking":false}' -tb "$threads" --log-disable &
server=$!
stop(){ kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true; }
trap 'stop; exit 0' TERM INT
# Docker restart policy handles process exits; healthcheck alone does not restart a hung process.
sleep 30
failures=0
while kill -0 "$server" 2>/dev/null; do
  if curl --fail --silent --max-time 3 http://127.0.0.1:11436/health >/dev/null; then failures=0; else failures=$((failures+1)); fi
  if [ "$failures" -ge 12 ]; then echo 'Classification health watchdog restarting failed process'; stop; exit 1; fi
  sleep 10 & wait $! || true
done
wait "$server" || exit 1
exit 1
