#!/bin/sh
set -eu
case "${DAMGOX2_AI_KIND:-}" in translation|classification) kind=$DAMGOX2_AI_KIND;; *) echo 'Invalid AI kind' >&2; exit 1;; esac
case "${DAMGOX2_AI_MODE:-serve}" in serve) exec "/opt/$kind/serve.sh";; init) exec "/opt/$kind/model-init.sh";; *) echo 'Invalid AI mode' >&2; exit 1;; esac
