# Perfetto investigation with android-perf-cli

## What the CLI captures

`perf run --perfetto` (or `"engines": { "perfetto": true }` in a scenario) records a **light-mode** system trace on the device, pulls it to `--artifacts-dir` (default: `perf-artifacts/` next to your report), and references the file in `report.md`.

The JVM **does not** parse trace protobuf. Offline analysis uses the host **`trace_processor`** binary.

## Three ways to investigate a trace

| Tool | When to use |
|------|-------------|
| **[Perfetto UI](https://ui.perfetto.dev)** | Interactive timelines, slice drill-down, sharing traces. |
| **`perf analyze`** | Reproducible, scripted metrics for CI and Markdown reports. |
| **Cursor skills** | Open-ended root cause (`perfetto-trace-analysis`) or custom SQL (`perfetto-sql`). |

## Install `trace_processor`

```bash
curl -LO https://get.perfetto.dev/trace_processor
chmod +x trace_processor
mv trace_processor ~/.perf-cli/trace_processor
```

Or put `trace_processor` on your `PATH`.

## `perf analyze` (Level 2)

```bash
perf analyze rendering perf-artifacts/perfetto-*.perfetto-trace --package com.example.app
perf analyze startup perf-artifacts/perfetto-*.perfetto-trace --output json
perf analyze cpu perf-artifacts/perfetto-*.perfetto-trace
perf analyze memory perf-artifacts/perfetto-*.perfetto-trace
```

Domains: `startup`, `rendering`, `memory`, `cpu`.

Optional flags: `--package`, `--window-ms`, `--from-ts`, `--output json|md`, `--max-main-thread-blocked-ms` (exit `1` on breach).

## Auto-analyze after `perf run`

- CLI: `perf run --perfetto --auto-analyze ...`
- Scenario: `"engines": { "perfetto": true, "perfettoAutoAnalyze": true }`
- Full profile (`--full` / `"profile": "full"`) enables auto-analyze with Perfetto.

Analyze failures are **best-effort** — the run still completes unless trace thresholds breach.

## Scenario thresholds (trace)

```json
"thresholds": {
  "maxMainThreadBlockedMs": 500
}
```

Evaluated when `perfettoAnalyze` metrics are present on the report (after auto-analyze or manual analyze integration).

## Cursor skills (deep dive)

When canned metrics are not enough:

- **`perfetto-trace-analysis`** — hypothesis-driven investigation, domain hints, chain-of-evidence scratchpad.
- **`perfetto-sql`** — natural language → validated Perfetto SQL.

Point the agent at your pulled trace under `perf-artifacts/`.
