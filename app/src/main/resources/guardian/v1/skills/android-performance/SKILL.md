---
name: android-performance
description: Investigate Android app performance (startup, rendering, memory, CPU) by running the perf CLI against Perfetto traces and reading its JSON. Use when asked to analyze a Perfetto trace, find jank, slow startup, memory growth or CPU hotspots, or to gate a build on performance metrics.
---

# Android performance with `perf`

`perf` is a CLI that turns Perfetto traces into metrics. Run it, read its output, and report
evidence. Do not guess numbers that the tool did not print.

## Before you start

- `perf analyze` works offline on a trace file. It needs `trace_processor` on PATH
  (`pip install perfetto`). No device is required.
- `perf run` needs a connected device or emulator and the Android SDK configured (`perf init`).
- Confirm the binary works: `perf version`.

## Analyze a trace

```
perf analyze <startup|rendering|memory|cpu> <trace.perfetto-trace> --output json
```

| Domain | Question it answers | Known metric keys |
|--------|---------------------|-------------------|
| `startup` | How long did launch take and where? | trace-dependent `android_startup` metrics |
| `rendering` | Is the UI dropping frames or blocking the main thread? | `main_thread_blocked_ms`, `gfx_slice_count`, `gfx_total_duration_ms` |
| `memory` | Which processes hold memory? | `process_count`, `max_rss_kb` |
| `cpu` | Which slices dominate CPU time? | `top_slice_name`, `top_slice_duration_ms` |

Examples:

```
perf analyze startup ./startup.perfetto-trace --output json --package com.example.app
perf analyze rendering ./app.perfetto-trace --output json --max-main-thread-blocked-ms 50
perf analyze memory ./app.perfetto-trace --output json
perf analyze cpu ./app.perfetto-trace --output json
```

Metric keys are informational and may change between query pack versions. Treat any key as
possibly absent and say so instead of inventing a value.

## Capture a trace or session

```
perf run --package com.example.app --duration 30 -o report.md
perf run --package com.example.app --full --startup --perfetto -o report.md
```

`--output` on `perf run` is the Markdown report path, not a format switch. `perf run` does not
emit JSON.

## Exit codes

- `0`: success. For `analyze --output json`, valid JSON is on stdout.
- `1`: runtime or gate failure (missing `trace_processor`, missing trace, threshold breached,
  no or ambiguous device). The cause is on stderr. On a threshold breach the JSON is still on
  stdout, so read stdout and stderr separately.
- `2`: usage error. Do not retry with the same arguments; fix the invocation first (exit 2
  always repeats).

## How to report findings

1. Name the domain and the trace you analyzed.
2. Quote the metric keys and values exactly as printed.
3. Separate observation (what the metric says) from hypothesis (what might cause it).
4. If a metric you expected is absent, state that and what trace data would provide it.
5. Suggest the smallest next measurement, for example a longer trace or `--window-ms` around the
   slow interval.
