#!/bin/sh
# Lightweight Gradle launcher for Android IDEs where the standard Gradle wrapper JAR is unavailable.
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROP="$BASE_DIR/gradle/wrapper/gradle-wrapper.properties"
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROP" | sed 's/\\:/:/g' | sed 's/\\\\/\\/g')
GRADLE_VERSION=$(printf '%s' "$DIST_URL" | sed -n 's/.*gradle-\([^/]*\)-bin\.zip.*/\1/p')
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/custom-dists/gradle-$GRADLE_VERSION"
GRADLE_HOME="$CACHE/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 15 -o "$ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$DIST_URL"
    else
      echo "Error: curl or wget is required to download Gradle." >&2
      exit 1
    fi
  fi
  rm -rf "$CACHE/extracted"
  mkdir -p "$CACHE/extracted"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$CACHE/extracted"
  else
    echo "Error: unzip is required to extract Gradle." >&2
    exit 1
  fi
  FOUND=$(find "$CACHE/extracted" -maxdepth 3 -type f -path '*/bin/gradle' -print -quit)
  if [ -z "$FOUND" ]; then
    echo "Error: Gradle distribution did not contain bin/gradle." >&2
    exit 1
  fi
  FOUND_HOME=$(dirname "$(dirname "$FOUND")")
  rm -rf "$GRADLE_HOME"
  mv "$FOUND_HOME" "$GRADLE_HOME"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
