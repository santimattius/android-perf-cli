---
name: android-jank-review
description: Review Android UI jank and dropped frames using perf rendering metrics and Perfetto traces. Use when scrolling or animation stutters, frame times spike, or a change may have hurt rendering.
---

# Jank review

Builds on the `android-performance` skill. Read it first for commands, flags and exit codes.

## Frame budget

A frame must finish within 16.7 ms at 60 Hz and 8.3 ms at 120 Hz. Android vitals count frames over
16 ms as slow and over 700 ms as frozen. Confirm the display refresh rate before judging.

## Measure

```
perf run --package com.example.app --duration 30 -o report.md
perf analyze rendering ./app.perfetto-trace --output json --max-main-thread-blocked-ms 50
```

Rendering keys: `main_thread_blocked_ms`, `gfx_slice_count`, `gfx_total_duration_ms`.

- High `main_thread_blocked_ms` points at work on the UI thread.
- Many or long graphics slices point at rendering cost (overdraw, large layouts, heavy draws).
- If a key is absent, say so and do not estimate it.

For CPU causes, also run:

```
perf analyze cpu ./app.perfetto-trace --output json
```

## Exit behavior

`--max-main-thread-blocked-ms` exits `1` on a breach but still prints the JSON on stdout. Read
stdout and stderr separately.

## How to report

1. State the screen or interaction analyzed and the trace used.
2. Quote the rendering metrics exactly as printed, with units.
3. Separate observation (what the numbers say) from hypothesis (the likely cause); mark
   hypotheses unverified.
4. Point to code only after the trace names a suspect, with file and line.
5. Name the smallest next measurement, for example tracing the exact scroll that stutters.
