#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="neurokaraoke-linux-builder"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to build Linux artifacts. Please install Docker Desktop." >&2
  exit 1
fi

build_image() {
  local platform="$1"
  docker build --platform "$platform" -t "$IMAGE_NAME" -f "$PROJECT_ROOT/Dockerfile.linux-build" "$PROJECT_ROOT"
}

run_build() {
  local platform="$1"
  local arch="$2"
  local node_modules_volume="neurokaraoke_node_modules_linux_${arch}"

  local eb_cache_host="$HOME/Library/Caches/electron-builder"
  local electron_cache_host="$HOME/Library/Caches/electron"
  local eb_cache_mount=""
  local electron_cache_mount=""

  if [ -d "$eb_cache_host" ]; then
    eb_cache_mount="-v $eb_cache_host:/root/.cache/electron-builder"
  fi
  if [ -d "$electron_cache_host" ]; then
    electron_cache_mount="-v $electron_cache_host:/root/.cache/electron"
  fi

  docker run --rm \
    --platform "$platform" \
    -v "$PROJECT_ROOT:/app" \
    -v "$node_modules_volume:/app/node_modules" \
    $eb_cache_mount \
    $electron_cache_mount \
    -w /app \
    "$IMAGE_NAME" \
    bash -lc "echo \"Container arch: \$(uname -m)\" && npm_config_platform=linux npm_config_arch=${arch} yarn install --frozen-lockfile && npm_config_platform=linux npm_config_arch=${arch} yarn build:linux --${arch}"
}

echo "Building Linux artifacts via Docker..."

echo "Building image for linux/amd64..."
build_image "linux/amd64"
run_build "linux/amd64" "x64"

echo "Building image for linux/arm64..."
build_image "linux/arm64"
run_build "linux/arm64" "arm64"

echo "Done. Artifacts are in ./dist"
