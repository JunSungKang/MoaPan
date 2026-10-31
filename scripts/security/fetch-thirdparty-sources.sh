#!/bin/sh
# Exact-version source archives for redistributed EPL libraries, not application dependencies.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
out="$root/build/third-party-sources"
mkdir -p "$out"
tab=$(printf '\t')
while IFS="$tab" read -r name sha url; do
 case "$name" in logback-classic-1.5.38-sources.jar|logback-core-1.5.38-sources.jar|aspectjweaver-1.9.25.1-sources.jar) ;; *) echo 'Unexpected source artifact'; exit 1;; esac
 case "$url" in https://repo.maven.apache.org/maven2/*) ;; *) echo 'Unexpected source host'; exit 1;; esac
 if [ ! -f "$out/$name" ]; then curl --fail --location --proto '=https' --tlsv1.2 --output "$out/$name" "$url"; fi
 if command -v sha256sum >/dev/null 2>&1; then printf '%s  %s\n' "$sha" "$out/$name" | sha256sum -c -; else printf '%s  %s\n' "$sha" "$out/$name" | shasum -a 256 -c -; fi
done < licenses/source-archives.tsv
