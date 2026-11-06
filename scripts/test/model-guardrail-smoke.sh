#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
[ -f "$root/build/reports/model-smoke.classpath" ] || { echo 'Run modelSmokeClasspath first'; exit 1; }
classpath=$(cat "$root/build/reports/model-smoke.classpath")
image=$(docker image inspect --format '{{.Id}}' "${DAMGOX2_SMOKE_IMAGE:-damgox2-local:1.0.0}")
resume_worker=no
if [ "${DAMGOX2_SMOKE_PAUSE_WORKER:-true}" = true ] && [ "$(docker inspect --format '{{.State.Running}}' damgox2-worker-1 2>/dev/null || true)" = true ]; then
  docker stop damgox2-worker-1 >/dev/null
  resume_worker=yes
fi
trap 'if [ "$resume_worker" = yes ]; then docker start damgox2-worker-1 >/dev/null; fi' EXIT HUP INT TERM
# Existing internal model networks only. This synthetic runner never connects to the DB.
docker run --rm --name damgox2-model-guardrail-smoke --network "${DAMGOX2_SMOKE_TRANSLATION_NETWORK:-damgox2_translation}" --network "${DAMGOX2_SMOKE_CLASSIFICATION_NETWORK:-damgox2_classification}" \
 --read-only --tmpfs /tmp:size=64m,mode=1777 --cap-drop ALL --security-opt no-new-privileges:true --memory 512m --cpus 1 \
 --mount "type=bind,source=$root/build,target=$root/build,readonly" \
 --mount "type=bind,source=$root/.local/gradle-home/caches,target=$root/.local/gradle-home/caches,readonly" \
 --mount "type=bind,source=$root/.local/translation/api.key,target=/run/secrets/translation_key,readonly" \
 --mount "type=bind,source=$root/.local/classification/api.key,target=/run/secrets/classification_key,readonly" \
 --entrypoint java "$image" -cp "$classpath" com.moapan.ai.ModelGuardrailSmoke
