#!/bin/sh
set -eu
if find src -type f \( -name '*.tsx' -o -name '*.jsx' -o -name '*.ts' -o -name '*.py' \) | read -r unused; then echo 'Forbidden source language' >&2; exit 1; fi
if find .github/workflows -type f 2>/dev/null | read -r unused; then echo 'CI/CD explicitly excluded' >&2; exit 1; fi
if rg -n 'innerHTML|eval\(|new Function\(|https?://.*(fonts.googleapis|google-analytics)|user_interests|reading_history|click_events' src/main; then echo 'Unsafe rendering, tracking or forbidden storage' >&2; exit 1; fi
