#!/usr/bin/env bash
# Builds the relauncher_native cdylib for all supported targets.
# Targets that fail are skipped. Successful outputs are copied into prebuild-resources/.
#
# Prerequisites:
#   rustup               | https://rustup.rs
#   cross                | cargo install cross (Linux targets, uses Docker/Podman)
#   cargo-xwin           | cargo install cargo-xwin (Windows targets, downloads MSVC CRT)
#   cargo-zigbuild + zig | cargo install cargo-zigbuild && install zig (macOS targets)
#
# Rust targets must be installed:
#   rustup target add x86_64-unknown-linux-gnu aarch64-unknown-linux-gnu \
#     x86_64-apple-darwin aarch64-apple-darwin \
#     x86_64-pc-windows-msvc aarch64-pc-windows-msvc
#
# Usage:
#   ./build-natives.sh          # build all targets (skips failures)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NATIVE_DIR="$SCRIPT_DIR/native"
PREBUILD_DIR="$SCRIPT_DIR/prebuild-resources/relauncher-natives"

# target-triple | os-arch label | library filename | build tool
TARGETS=(
  "x86_64-unknown-linux-gnu|linux-x86_64|librelauncher_native.so|cross"
  "aarch64-unknown-linux-gnu|linux-aarch64|librelauncher_native.so|cross"
  "x86_64-apple-darwin|macos-x86_64|librelauncher_native.dylib|zigbuild"
  "aarch64-apple-darwin|macos-aarch64|librelauncher_native.dylib|zigbuild"
  "x86_64-pc-windows-msvc|windows-x86_64|relauncher_native.dll|xwin"
  "aarch64-pc-windows-msvc|windows-aarch64|relauncher_native.dll|xwin"
)

succeeded=0
failed=0

for entry in "${TARGETS[@]}"; do
  IFS='|' read -r triple label libname tool <<< "$entry"

  echo "--- Building $triple ($label) ---"

  case "$tool" in
    xwin)     cmd=(cargo xwin build --target "$triple" --release) ;;
    cross)    cmd=(cross build --target "$triple" --release) ;;
    zigbuild) cmd=(cargo zigbuild --target "$triple" --release) ;;
    *)        cmd=(cargo build --target "$triple" --release) ;;
  esac

  if (cd "$NATIVE_DIR" && "${cmd[@]}"); then
    src="$NATIVE_DIR/target/$triple/release/$libname"
    if [[ -f "$src" ]]; then
      dest="$PREBUILD_DIR/$label"
      mkdir -p "$dest"
      cp "$src" "$dest/$libname"
      echo "  -> copied to $dest/$libname"
      ((succeeded++))
    else
      echo "  -> build succeeded but $libname not found, skipping"
      ((failed++))
    fi
  else
    echo "  -> FAILED, skipping"
    ((failed++))
  fi

  echo
done

echo "Done: $succeeded succeeded, $failed failed"
