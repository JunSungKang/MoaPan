#!/bin/sh
set -eu
threads=${TRANSLATION_THREADS:-2}
case "$threads" in ''|*[!0-9]*) echo 'Invalid translation thread count'; exit 1;; esac
[ "$threads" -ge 1 ] && [ "$threads" -le 64 ] || exit 1
[ -s /run/secrets/translation_key ] || { echo 'Translation key missing'; exit 1; }
printf '%s  %s\n' a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3 /models/index-translate-2b-q8.gguf | sha256sum -c -
# No outgoing network, web UI, RPC, prompt logs, GPU or dynamic model download.
llama-server -m /models/index-translate-2b-q8.gguf --alias moapan-translation --host 0.0.0.0 --port 11435 -c 4096 -b 64 -ub 64 -np 1 -ngl 0 -t "$threads" --no-webui --offline --no-cache-prompt --cache-ram 0 --api-key-file /run/secrets/translation_key --reasoning off --log-disable &
server=$!
stop(){ kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true; }
trap 'stop; exit 0' TERM INT
# Docker restart policy handles process exits; healthcheck alone does not restart a hung process.
sleep 30
failures=0
while kill -0 "$server" 2>/dev/null; do
  if curl --fail --silent --max-time 3 http://127.0.0.1:11435/health >/dev/null; then failures=0; else failures=$((failures+1)); fi
  if [ "$failures" -ge 12 ]; then echo 'Translation health watchdog restarting failed process'; stop; exit 1; fi
  sleep 10 & wait $! || true
done
wait "$server" || exit 1
exit 1
