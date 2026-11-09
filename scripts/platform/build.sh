#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
export TMPDIR="$PWD/.local/tmp"
mkdir -p "$TMPDIR"
node scripts/platform/validate-images.mjs --common
if [ "$#" -eq 0 ]; then set -- app proxy ai; fi
exec docker buildx bake -f docker-bake.hcl --load "$@"
