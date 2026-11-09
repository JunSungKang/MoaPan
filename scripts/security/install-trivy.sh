#!/bin/sh
# Official release, independently checksummed and Sigstore-verified candidate.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
[ -d "$root/.local" ] && [ "$(cd "$root/.local" && pwd -P)" = "$root/.local" ] || exit 1
export TMPDIR="$root/.local/tmp" TUF_ROOT="$root/.local/sigstore" XDG_CACHE_HOME="$root/.local/tool-cache"
mkdir -p "$TMPDIR" "$root/.local/bin"
case "$(uname -s):$(uname -m)" in
 Darwin:arm64) asset=macOS-ARM64; checksum=ae7ea358326960406b703c77b2ac490190797d80b306b5b50c2a27221da0a5b8;;
 Darwin:x86_64) asset=macOS-64bit; checksum=e08bb9ea449ecf66c6bfb03cbb8697c503ff33d35fc585c9747e3eaeb7ed4c7b;;
 Linux:arm64|Linux:aarch64) asset=Linux-ARM64; checksum=ab9d51f86e6a5fb936f605faff6b593ac51bc378fff4d3e8b9d58358b9c367c3;;
 Linux:x86_64) asset=Linux-64bit; checksum=bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf;;
 *) echo 'Unsupported platform' >&2; exit 1;;
esac
cosign=${COSIGN_BIN:-cosign}
command -v "$cosign" >/dev/null 2>&1 || { echo 'A verified cosign is required to authenticate the official release' >&2; exit 1; }
work=$(mktemp -d "$TMPDIR/trivy-install.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
name="trivy_0.76.2_$asset.tar.gz"
base=https://github.com/aquasecurity/trivy/releases/download/v0.76.2
for file in "$name" "$name.sigstore.json"; do
 curl --fail --location --proto '=https' --tlsv1.2 --silent --show-error --output "$work/$file" "$base/$file"
done
printf '%s  %s\n' "$checksum" "$work/$name" | shasum -a 256 -c -
"$cosign" verify-blob --bundle "$work/$name.sigstore.json" \
 --certificate-identity 'https://github.com/aquasecurity/trivy/.github/workflows/reusable-release.yaml@refs/tags/v0.76.2' \
 --certificate-oidc-issuer https://token.actions.githubusercontent.com "$work/$name"
tar -xzf "$work/$name" -C "$work" trivy
"$work/trivy" --version | grep -F 'Version: 0.76.2'
cp "$work/trivy" "$root/.local/bin/trivy.candidate"
echo 'Verified official .local/bin/trivy.candidate ready; self-scan and review actual binary findings before promoting to trivy.'
