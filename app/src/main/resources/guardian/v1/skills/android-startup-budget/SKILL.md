---
name: android-startup-budget
description: Measure Android app startup against a time budget using perf and explain where launch time goes. Use when cold start feels slow, a release regresses startup, or you need a startup gate for CI.
---

# Startup budget

Builds on the `android-performance` skill. Read it first for commands, flags and exit codes.

## Budgets

Android vitals treat these as excessive: cold start 5 s or more, warm start 2 s or more, hot
start 1.5 s or more. Treat them as ceilings, not goals. Agree a stricter budget with the user
before judging a result.

## Measure on a device

```
perf run --package com.example.app --startup --duration 30 -o report.md
perf run --package com.example.app --startup --activity com.example.app/.MainActivity -o report.md
```

- `--startup` measures one cold start at the beginning of the session.
- `--activity` sets the launch component when the default launcher activity is wrong.

## Analyze a trace offline

```
perf analyze startup ./startup.perfetto-trace --output json --package com.example.app
```

The startup keys depend on the trace. Quote what is present and do not assume a key exists.

## Compare against history

```
perf history --package com.example.app --window 10
```

Use it to tell a one-off from a trend before calling a regression.

## How to report

1. State cold, warm or hot, the device or trace, and the budget used.
2. Quote the measured startup values exactly as printed, with units.
3. Say pass or fail against the agreed budget, and by how much.
4. Separate observation from hypothesis; mark hypotheses unverified.
5. Name the smallest next measurement, for example repeating the run to check variance.
