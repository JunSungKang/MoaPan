#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
MOAPAN_ROOT=$(pwd -P)
case "$(uname -s)" in Darwin) target_os=darwin;; Linux) target_os=linux;; *) echo 'Unsupported host OS' >&2;exit 1;; esac
case "$(uname -m)" in arm64|aarch64) target_arch=arm64;; x86_64) target_arch=amd64;; *) echo 'Unsupported host architecture' >&2;exit 1;; esac
mkdir -p "$MOAPAN_ROOT/.local/bin"
docker build --target proxy-build -t moapan-proxy-build:2.11.7 .
docker run --rm -v "$MOAPAN_ROOT:/workspace" -w /workspace/scripts/backup/backup-tools -e GOOS="$target_os" -e GOARCH="$target_arch" --entrypoint sh moapan-proxy-build:2.11.7 -c 'go build -mod=readonly -trimpath -ldflags "-X main.Version=v1.3.2+moapan-security.1" -o /workspace/.local/bin/age filippo.io/age/cmd/age && go build -mod=readonly -trimpath -ldflags "-X main.Version=v1.3.2+moapan-security.1" -o /workspace/.local/bin/age-keygen filippo.io/age/cmd/age-keygen'
