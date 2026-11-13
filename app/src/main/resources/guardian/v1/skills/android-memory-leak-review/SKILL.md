---
name: android-memory-leak-review
description: Investigate Android memory growth, leaks and low-memory kills using perf heap capture and memory metrics. Use when memory climbs over a session, the app is killed in the background, or a leak is suspected.
---

# Memory and leak review

Builds on the `android-performance` skill. Read it first for commands, flags and exit codes.

## Capture

```
perf run --package com.example.app --duration 120 --heap --exit-signals -o report.md
perf run --package com.example.app --full --artifacts-dir ./artifacts -o report.md
```

- `--heap` triggers a heap dump at the end of the session.
- `--exit-signals` exits `1` when logcat confirms a low-memory kill, an ANR or a crash.
- `--artifacts-dir` keeps the pulled files for inspection.

## Analyze a trace

```
perf analyze memory ./app.perfetto-trace --output json
```

Known keys: `process_count`, `max_rss_kb`. A high `max_rss_kb` shows footprint, not a leak by
itself. A leak needs growth that survives garbage collection across repeated, identical actions.

## Common leak shapes to check, as hypotheses

- Activities or fragments held by a static, singleton or long-lived listener.
- Unclosed resources: cursors, streams, bitmaps.
- Coroutines or callbacks that outlive their lifecycle owner.

## How to report

1. State the scenario that was repeated, its duration, and the artifact analyzed.
2. Quote `max_rss_kb` and `process_count` exactly as printed, with units.
3. Say whether memory grew across repeated actions, and cite both measurements.
4. Separate observation from hypothesis; mark hypotheses unverified.
5. Name the smallest next step, for example repeating the action more times to confirm the trend.
