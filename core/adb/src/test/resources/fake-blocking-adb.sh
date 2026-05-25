#!/usr/bin/env bash
# Test stub: mimics `adb -s SERIAL …` then blocks on stdin like a silent logcat stream.
if [[ "${1:-}" == "-s" ]]; then
  shift 2
fi
echo "logcat-ready"
exec tail -f /dev/null
