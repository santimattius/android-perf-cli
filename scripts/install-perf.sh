#!/usr/bin/env bash
# Builds the fat JAR and prints a ready-to-paste `perf` alias for your shell.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ ! -x ./gradlew ]]; then
  echo "gradlew not found or not executable in $ROOT" >&2
  exit 1
fi
./gradlew :app:jar -q
JAR="$(ls -1 "$ROOT"/app/build/libs/perf-cli-*.jar | head -1)"
echo "Build OK: $JAR"
echo
echo "Session (current shell only):"
echo "  alias perf='java -jar $JAR'"
echo
echo "Permanent (zsh): append this line to ~/.zshrc"
echo "  alias perf='java -jar $JAR'"
