---
name: android-anr-triage
description: Triage Android ANRs (Application Not Responding) by detecting them with perf and finding what blocked the main thread. Use when an app freezes, a user or Play Console reports an ANR, or a session shows main-thread stalls.
---

# ANR triage

Builds on the `android-performance` skill. Read it first for commands, flags and exit codes.

An ANR happens when the main thread cannot serve input or lifecycle work in time. Typical limits:
input dispatch about 5 s, foreground broadcast about 10 s, foreground service start about 20 s.
Find what held the main thread, not just that it was held.

## Detect

```
perf run --package com.example.app --duration 60 --exit-signals --perfetto -o report.md
```

- `--exit-signals` makes the run exit `1` when logcat confirms an ANR, crash or low-memory kill.
  Read the report and stderr to see which signal fired.
- `--perfetto` records a trace you can analyze offline.

## Locate the blocking work

```
perf analyze rendering ./app.perfetto-trace --output json --max-main-thread-blocked-ms 100
perf analyze cpu ./app.perfetto-trace --output json
```

- `main_thread_blocked_ms` (rendering) is the main-thread blocked time. A value near the ANR
  limit explains the freeze; a low value points elsewhere (for example a binder or lock wait).
- `top_slice_name` and `top_slice_duration_ms` (cpu) name the dominant slice.

There is no ANR-specific analyze domain, so do not invent one. Metric keys may be absent: say so.

## Common causes to check, as hypotheses

- Disk or network I/O on the main thread.
- Synchronous binder calls or lock contention.
- Heavy work in `BroadcastReceiver.onReceive`, `Service.onCreate` or `Application.onCreate`.
- Main thread waiting on a background thread that is itself starved.

## How to report

1. State the signal that fired (ANR, crash, LMK) and the trace analyzed.
2. Quote `main_thread_blocked_ms` and the dominant slice exactly as printed.
3. Separate observation from hypothesis, and mark each hypothesis unverified.
4. Name the smallest next step, for example a longer trace around the stall or
   `--window-ms` with `--from-ts`.
