#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
jar="$root/.local/html-check/vnu.jar"
[ -f "$jar" ] || { echo 'HTML check NOT_RUN: provision the verified Nu validator described in the test report' >&2; exit 1; }
printf '%s  %s\n' 19f89e204cb881e2d88c5d0cd451df6c1ee8334e7fe626f6694b64a129f6a813 "$jar" | shasum -a 256 -c -
java_bin=${JAVA_HOME:+$JAVA_HOME/bin/}java
mkdir -p "$root/build/reports/html" "$root/.local/tmp"
"$java_bin" -Djava.io.tmpdir="$root/.local/tmp" -jar "$jar" --format json --stdout src/main/resources/static/*.html > "$root/build/reports/html/validation.json"
node -e 'const r=require(process.cwd()+"/build/reports/html/validation.json");if(r.messages.some(m=>m.type==="error"))process.exit(1);console.log("Nu HTML check passed",r.version,"messages",r.messages.length)'
