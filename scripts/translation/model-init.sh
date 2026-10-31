#!/bin/sh
set -eu
exec 9>/models/.init.lock
flock -n 9 || { echo 'Another model initializer is running'; exit 1; }
mkdir -p /models/licenses
cp /opt/translation/licenses/model/LICENSE.txt /models/licenses/LICENSE.txt
cp /opt/translation/licenses/model/SOURCE.json /models/licenses/SOURCE.json
model=/models/index-translate-2b-q8.gguf
sha=a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3
verify() { printf '%s  %s\n' "$sha" "$1" | sha256sum -c -; }
if [ -f "$model" ]; then verify "$model"; exit; fi
# Only one initialiser may write this volume; interrupted files are never activated.
curl --fail --location --proto '=https' --tlsv1.2 --retry 2 --connect-timeout 15 --max-time 1800 -o "$model.part" https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF/resolve/3a0eca6ce931d88d00614d427ab3ed8ff0743902/Index-Translate-2B.Q8_0.gguf
verify "$model.part"
mv "$model.part" "$model"
chmod 0444 "$model"
