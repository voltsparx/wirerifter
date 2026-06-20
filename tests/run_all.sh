#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
RUST_DIR="$ROOT/app/src/main/rust"

step() {
  printf "\n==> %s\n" "$1"
}

has_command() {
  command -v "$1" >/dev/null 2>&1
}

cd "$ROOT"

step "Repository hygiene"
if has_command rg; then
  if rg -n "wireshark|Wireshark|nmap|Nmap|NetSentry|netsentry|Gemini|firebase" app/src/main README.md docs LICENSE metadata.json; then
    printf "Repository hygiene scan found blocked legacy references.\n" >&2
    exit 1
  else
    code=$?
    if [ "$code" -gt 1 ]; then
      printf "Repository hygiene scan failed.\n" >&2
      exit "$code"
    fi
  fi
else
  printf "warning: ripgrep is not installed; skipping repository text scan.\n" >&2
fi

step "Rust formatting"
cd "$RUST_DIR"
if ! has_command cargo; then
  printf "cargo is required for Rust checks.\n" >&2
  exit 1
fi
cargo fmt -- --check
cargo check
cargo test

cd "$ROOT"

if [ "${SKIP_ANDROID:-0}" != "1" ]; then
  step "Android Gradle checks"
  HAS_SDK=0
  if [ -n "${ANDROID_HOME:-}" ] || [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    HAS_SDK=1
  elif [ -f "$ROOT/local.properties" ]; then
    SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -n 1)"
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ]; then
      HAS_SDK=1
    fi
  fi

  if [ "$HAS_SDK" = "1" ]; then
    if [ -x "$ROOT/gradlew" ]; then
      "$ROOT/gradlew" :app:compileDebugKotlin :app:testDebugUnitTest
    elif has_command gradle; then
      gradle :app:compileDebugKotlin :app:testDebugUnitTest
    else
      printf "Gradle is not installed and no Gradle wrapper is present.\n" >&2
      exit 1
    fi
  else
    printf "warning: Android SDK not configured; skipping Android Gradle checks.\n" >&2
  fi
fi

printf "\nAll available checks passed.\n"
