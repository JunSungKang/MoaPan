#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
export TMPDIR="$root/.local/tmp"
mkdir -p "$TMPDIR" "$root/.local/bin"
# Only this exact out-of-workspace lock and missing parents were explicitly authorized.
lock=/Users/kkkk/Desktop/jskang/03.workspace/01.project/operations/run/deploy.lock
mkdir -p "$(dirname "$lock")"
cc -Wall -Wextra -Werror scripts/platform/deploy-lock.c -o "$root/.local/bin/damgox2-deploy-lock"
"$root/.local/jdk-25.0.5+8/Contents/Home/bin/javac" -d "$root/.local/platform-ubuntu25-20261109/probes" scripts/platform/SmtpConnectionCheck.java
exec "$root/.local/bin/damgox2-deploy-lock" "$lock" node scripts/platform/deploy.mjs "$@"
