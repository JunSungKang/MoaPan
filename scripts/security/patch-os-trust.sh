#!/bin/sh
# Exact DER fingerprint from the official four-root mitigation; never match similar names.
# https://ops.java/emudhra/  emSign Root CA - G1 (additional precautionary root).
set -eu
command -v openssl >/dev/null
for cert in /usr/share/ca-certificates/mozilla/*.crt; do
  [ -f "$cert" ] || continue
  fingerprint=$(openssl x509 -in "$cert" -noout -fingerprint -sha256)
  fingerprint=$(printf '%s\n' "$fingerprint" | cut -d '=' -f 2 | tr -d ':' | tr 'A-F' 'a-f')
  if [ "$fingerprint" = 40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367 ]; then
    rm "$cert"
    echo "Removed official precautionary OS root SHA256=$fingerprint"
  fi
done
update-ca-certificates --fresh
