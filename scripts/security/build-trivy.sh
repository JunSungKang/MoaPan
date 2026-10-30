#!/bin/sh
# Official v0.76.1 source: GitHub-verified signed commit; reviewed dependency fixes.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
[ -d "$root/.local" ] && [ "$(cd "$root/.local" && pwd -P)" = "$root/.local" ] || { echo 'Workspace-local .local required'; exit 1; }
export TMPDIR="$root/.local/tmp"
mkdir -p "$TMPDIR" "$root/.local/bin" "$root/.local/downloads" "$root/build/reports/security"
case "$(uname -s)" in Darwin) target_os=darwin;; Linux) target_os=linux;; *) exit 1;; esac
case "$(uname -m)" in arm64|aarch64) target_arch=arm64;; x86_64) target_arch=amd64;; *) exit 1;; esac
commit=e713f67f7d54decdcd9b3132c9751833dcced698
archive="$root/.local/downloads/trivy-source-$commit.tar.gz"
if [ ! -f "$archive" ]; then curl --fail --location --proto '=https' --tlsv1.2 --output "$archive" "https://github.com/aquasecurity/trivy/archive/$commit.tar.gz"; fi
printf '%s  %s\n' 5bcd06d8bdae338978b8ef597cb23cd0ff2e710d1ea9097c1143ec6b07d362d6 "$archive" | shasum -a 256 -c -
work=$(mktemp -d "$TMPDIR/trivy-build.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
tar -xzf "$archive" -C "$work" --strip-components=1
docker build --target proxy-build -t moapan-proxy-build:2.11.7 .
relative=${work#"$root/"}
docker run --rm -v "$root:/workspace" -w "/workspace/$relative" -e GOOS="$target_os" -e GOARCH="$target_arch" -e CGO_ENABLED=0 -e GOTOOLCHAIN=local -e GOPATH=/workspace/.local/go -e GOMODCACHE=/workspace/.local/go-mod -e GOCACHE=/workspace/.local/go-cache -e TMPDIR=/workspace/.local/tmp -e GOMEMLIMIT=2GiB --entrypoint sh moapan-proxy-build:2.11.7 -c '
 set -eu
 actual_go=$(go version)
 case "$actual_go" in "go version go1.27.3 "*) ;; *) echo "Unexpected Go compiler"; exit 1;; esac
 go get github.com/go-jose/go-jose/v4@v4.1.6 golang.org/x/net@v0.62.0
 go build -p 2 -mod=readonly -buildvcs=false -trimpath -ldflags "-X github.com/aquasecurity/trivy/pkg/version/app.ver=0.76.1+moapan-security.1" -o ./trivy.candidate ./cmd/trivy
 go list -mod=readonly -deps ./cmd/trivy > /workspace/build/reports/security/trivy-imports.txt
 cp go.mod go.sum /workspace/build/reports/security/
 mv ./trivy.candidate /workspace/.local/bin/trivy.patched
'
"$root/.local/bin/trivy.patched" --version | grep -F '0.76.1+moapan-security.1'
echo 'Candidate .local/bin/trivy.patched built; inspect and self-scan before promoting to trivy.'
