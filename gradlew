#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.9"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap-dists/gradle-${GRADLE_VERSION}"
GRADLE_BIN="$CACHE_DIR/gradle-${GRADLE_VERSION}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  ARCHIVE="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ARCHIVE"
  fi
  if [ ! -x "$GRADLE_BIN" ]; then
    command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
    unzip -q -o "$ARCHIVE" -d "$CACHE_DIR"
  fi
fi

exec "$GRADLE_BIN" "$@"
