#!/bin/sh
# Reproduce the reviewed fix; an old manual expiry date is never renewed here.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
export TMPDIR="$root/.local/tmp"
mkdir -p "$TMPDIR" "$root/build/reports/security"
out="$root/build/reports/security"
rm -f "$out/jose-validation.json"
node scripts/security/jose-validation.mjs snapshot
docker buildx bake -f docker-bake.hcl proxy-check --load > "$out/jose-build.log" 2>&1
docker image inspect --format '{{.Id}}' damgox2-security-validation:ubuntu26.04-20261109 > "$out/jose-builder.id"
image=$(cat "$out/jose-builder.id")
docker run --rm --user "$(id -u):$(id -g)" --read-only --network none --cap-drop ALL --security-opt no-new-privileges:true --cpus 2 --memory 1g \
 -v "$root:/workspace" -w /proxy -e GOCACHE=/workspace/.local/go-cache -e TMPDIR=/workspace/.local/tmp --entrypoint sh "$image" -ec '
 go run /workspace/scripts/test/ProxySecurityCheck.go /workspace > /workspace/build/reports/security/proxy-boundaries.txt
 go run /workspace/scripts/security/InspectGoBinary.go /caddy golang.org/x/crypto/openpgp > /workspace/build/reports/security/caddy-functions.json
 go run /workspace/scripts/security/InspectGoBinary.go /workspace/.local/bin/trivy golang.org/x/crypto/openpgp > /workspace/build/reports/security/trivy-functions.json
'
docker run --rm --user "$(id -u):$(id -g)" --read-only --network none --cap-drop ALL --security-opt no-new-privileges:true --entrypoint sh "$image" -ec '
 cd /proxy/patched-jose
 sha256sum -c /proxy/patches/modified.sha256
 sha256sum /caddy
' > "$out/jose-runtime-checks.txt"
docker run --rm --user "$(id -u):$(id -g)" --read-only --cap-drop ALL --security-opt no-new-privileges:true --cpus 4 --memory 4g \
 -v "$root:/workspace" -w /proxy -e GOTOOLCHAIN=local -e GOTELEMETRY=off -e GOENV=off \
 -e GOPATH=/workspace/.local/go -e GOMODCACHE=/workspace/.local/go-mod \
 -e GOCACHE=/workspace/.local/go-cache -e TMPDIR=/workspace/.local/tmp --entrypoint sh "$image" -ec '
 go version
 go test -p 2 -count=1 -mod=readonly -json -timeout 120s github.com/go-jose/go-jose/v3/... > /workspace/build/reports/security/jose-patched.jsonl
 # A pristine host cache need not contain the original, because /proxy replaces
 # it with the patched local module. Fetch this exact checksum-verified version
 # explicitly so the unpatched negative control is reproducible on first run.
 go mod download github.com/go-jose/go-jose/v3@v3.0.5
 work=$(mktemp -d "$TMPDIR/jose-negative.XXXXXX")
 trap '\''rm -rf "$work"'\'' EXIT HUP INT TERM
 go run /workspace/scripts/security/CopyModule.go "$GOMODCACHE/github.com/go-jose/go-jose/v3@v3.0.5" "$work"
 cp /proxy/patches/public_api_test.go "$work/"
 # This fifth public regression has no dependency on a backport-only symbol.
 sed -n '\''1,/^func TestDamGoX2DegeneratePrimesRejectedWithoutExpensiveValidation/ p'\'' /proxy/patches/jose_security_test.go | sed '\''$d'\'' > "$work/rsa_public_security_test.go"
 # Only imports needed by this test; the upstream v3 public API remains unchanged.
 sed -i '\''/"crypto\/rand"/d; /"crypto\/rsa"/d; /"time"/d'\'' "$work/rsa_public_security_test.go"
 cd "$work"
 if go test -p 2 -count=1 -mod=mod -json -timeout 120s -run '\''TestDamGoX2OversizedRSARejectedBeforeValidation|TestEmbeddedPrivateJWKRejected|TestGeneralSerializationUnprotectedHeader|TestJWERejectsShort(GCM|CBCHMAC)CEKForDeclaredContentEncryption'\'' . > /workspace/build/reports/security/jose-unpatched.jsonl; then
  echo "Unpatched negative control unexpectedly passed" >&2; exit 1
 fi
'
for id in GHSA-mgcc-cww5-fp2q GHSA-4w3g-jfj2-cw2q GHSA-vvm8-gf78-28g4; do
 curl --fail --location --proto '=https' --tlsv1.2 --silent --show-error \
  --output "$out/$id.json" "https://api.github.com/repos/go-jose/go-jose/security-advisories/$id"
done
node scripts/security/jose-validation.mjs record
