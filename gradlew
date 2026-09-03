#!/bin/sh
set -eu
VERSION=9.3.1
CACHE="${HOME}/.gradle/vueo-bootstrap/gradle-${VERSION}"
ZIP="${HOME}/.gradle/vueo-bootstrap/gradle-${VERSION}-bin.zip"
if [ ! -x "$CACHE/bin/gradle" ]; then
  mkdir -p "$(dirname "$ZIP")"
  URL="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "curl or wget is required for the first Gradle bootstrap." >&2
    exit 1
  fi
  rm -rf "$CACHE"
  unzip -q "$ZIP" -d "$(dirname "$CACHE")"
fi
exec "$CACHE/bin/gradle" "$@"
