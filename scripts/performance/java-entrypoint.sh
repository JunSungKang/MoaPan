#!/bin/sh
set -eu
# Keep operator overrides, while giving web and worker independent measured budgets.
case "${DAMGOX2_ROLE:-web}" in
  worker) heap=192; pool=6; idle=1;;
  *) heap=256; pool=8; idle=2;;
esac
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xms32m -Xmx${heap}m -XX:+UseCompactObjectHeaders -XX:ReservedCodeCacheSize=96m -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:-ShrinkHeapInSteps -Djava.io.tmpdir=/tmp -Duser.timezone=UTC}"
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"
export DAMGOX2_DB_POOL_MAX="${DAMGOX2_DB_POOL_MAX:-$pool}"
export DAMGOX2_DB_POOL_MIN="${DAMGOX2_DB_POOL_MIN:-$idle}"
exec java -jar /app/damgox2.jar "$@"
