# perf — Agent Skill

## Overview

`perf` is a command-line performance monitoring tool for Android apps running on real devices or
emulators. The binary name is `perf`. It is distributed as a GraalVM native binary (single
self-contained executable, no JVM required) and as a fat JAR invocable via
`java -jar perf-cli-*.jar`. Both forms share the identical command surface — invoke as `perf
<command> [flags]` or `java -jar perf-cli-*.jar <command> [flags]`.

---

## Prerequisites

- **ADB device or emulator** — `perf run` requires a connected Android device or a running AVD.
  `perf analyze` does NOT require a device (offline trace analysis).
- **`trace_processor` on PATH** — required only for `perf analyze`. Install via
  `pip install perfetto` or download from the [Perfetto releases page](https://github.com/google/perfetto/releases).
  If the binary is missing, `perf analyze` exits 1 and names the missing binary on stderr.
- **SDK configured** — run `perf init` once to save the Android SDK path. Alternatively, run
  `perf sdk install` to download platform-tools (includes `adb`) and configure in one step.

---

## Command Map

| Command          | Purpose                                                                 | Machine-readable output         |
|------------------|-------------------------------------------------------------------------|---------------------------------|
| `perf init`      | Configure Android SDK path and optional default AVD.                    | No                              |
| `perf sdk`       | SDK sub-commands (`install` bootstraps platform-tools / `adb`).         | No                              |
| `perf run`       | Monitor a live app session; collect metrics; write a Markdown report.   | No (Markdown file only)         |
| `perf analyze`   | Analyze an offline Perfetto trace via `trace_processor`.                | Yes — `--output json` on stdout |
| `perf history`   | Show rolling history of past `perf run` sessions for a package.         | No                              |
| `perf guardian`  | Install the Android performance skill and persona into a repository.    | No                              |
| `perf version`   | Print the installed version string.                                     | No                              |

---

### perf init

Configure the Android SDK path (and optionally a default AVD) on the host machine. Settings are
stored at `~/.config/perf-cli/config.json`.

**Synopsis**

```
perf init [--install-sdk] [-d <path>] [--android-sdk <path>] [--avd <name>]
```

**Key flags**

| Flag | Type | Purpose |
|------|------|---------|
| `--install-sdk` | boolean | Download Android command-line tools and install platform-tools. |
| `-d`, `--directory <path>` | path | SDK install directory when using `--install-sdk`. |
| `--android-sdk <path>` | path | Path to an existing Android SDK root. |
| `--avd <name>` | string | Default AVD name to launch when no device is connected. |

**Example**

```bash
# Save the SDK path you already have
perf init --android-sdk $ANDROID_HOME

# Download and install SDK tools in one step
perf init --install-sdk -d ~/android-sdk
```

---

### perf sdk

Sub-commands for SDK management. Currently supports `install`.

**Synopsis**

```
perf sdk install
```

**Example**

```bash
perf sdk install
```

---

### perf run

Monitor a live Android app session. Collects rendering metrics, logcat signals, optional startup
timings, optional heap dump, optional Perfetto trace, optional wake-lock stats, and optional
workflow automation. Writes a Markdown report at completion.

IMPORTANT: `--output`/`-o` on `perf run` is the **file path** for the Markdown report — it is NOT
a format switch. `perf run` does NOT emit JSON in this version.

**Synopsis**

```
perf run --package <pkg> [flags]
```

**Key flags**

| Flag | Type | Purpose |
|------|------|---------|
| `-p`, `--package <text>` | string (required) | Application package name. |
| `-d`, `--duration <int>` | int (seconds) | Monitoring duration. |
| `-o`, `--output <path>` | file path | Output path for the Markdown report (default: `report.md`). |
| `--scenario <path>` | file path | Path to a scenario JSON file (engines, thresholds, workflow, build). |
| `--full` | boolean | Heavy profile: heap + Perfetto + startup + Vitals preset. |
| `--startup` | boolean | Measure cold start once at the beginning of the session. |
| `--activity <text>` | string | Explicit launch component for cold start. |
| `--perfetto` | boolean | Record a Perfetto system trace. |
| `--auto-analyze` | boolean | After Perfetto capture, run offline `perf analyze` automatically. |
| `--heap` | boolean | Trigger a heap dump at end of session. |
| `--wakelocks` | boolean | Measure partial wake locks via `dumpsys batterystats`. |
| `--exit-signals` | boolean | Detect confirmed ANR, crash, LMK events via logcat. |
| `--min-score <int>` | int (0–100) | Fail (exit 1) when perf score falls below this value. |
| `--apk <path>` | file path | APK to install before monitoring. |
| `--project-dir <path>` | dir path | Android Gradle project root. |
| `--build-variant <text>` | string | With `--project-dir`, run `gradlew assemble<Variant>`. |
| `-s`, `--serial <text>` | string | ADB device serial. |
| `--skip-emulator` | boolean | Do not auto-launch the default AVD. |
| `--emulator-headless` | boolean | When auto-launching an AVD, use `-no-window`. |
| `--artifacts-dir <path>` | dir path | Store screenshots and pulled trace files. |
| `--history-dir <path>` | dir path | Override session history directory. |
| `--skip-android-docs` | boolean | Do not run android docs search. |

**Example**

```bash
# Basic 30-second session
perf run --package com.example.app --duration 30 -o report.md

# Full profile with startup measurement
perf run --package com.example.app --full --startup -o report.md

# CI: fail if score drops below 80
perf run --package com.example.app --scenario scenarios/ci.json --min-score 80 -o report.md
```

---

### perf analyze

Analyze an offline Perfetto trace file using the host `trace_processor` binary. Supports four
domains: `startup`, `rendering`, `memory`, `cpu`. Does NOT require a connected device.

`--output json` emits a single JSON object to stdout — the only machine-readable output in `perf`.

**Synopsis**

```
perf analyze <startup|rendering|memory|cpu> <trace.perfetto-trace> [flags]
```

**Key flags**

| Flag | Type | Purpose |
|------|------|---------|
| `--output <json\|md>` | choice | Output format: `json` (stdout) or `md` (Markdown to stdout). Default: `md`. |
| `--package <text>` | string | Target app package (recorded in output; future SQL filter). |
| `--window-ms <long>` | long (ms) | Analysis window length in milliseconds. |
| `--from-ts <long>` | long (ns) | Window start timestamp in nanoseconds. |
| `--max-main-thread-blocked-ms <long>` | long (ms) | Fail (exit 1) if `main_thread_blocked_ms` exceeds this value. Works with any domain that produces this metric. |

**Example**

```bash
# Rendering analysis with JSON output
perf analyze rendering ./app.perfetto-trace --output json

# Startup with threshold gate
perf analyze rendering ./app.perfetto-trace --output json --max-main-thread-blocked-ms 50

# Startup metrics offline
perf analyze startup ./startup.perfetto-trace --output json --package com.example.app
```

---

### perf history

Show rolling history of past `perf run` sessions for a given package.

**Synopsis**

```
perf history [flags]
```

**Key flags**

| Flag | Type | Purpose |
|------|------|---------|
| `-p`, `--package <text>` | string | Application package name to look up history for. |
| `--window <int>` | int | Rolling window size (number of most-recent runs). Default: 30. |
| `--history-dir <path>` | dir path | Override history directory. |

**Example**

```bash
perf history --package com.example.app --window 10
```

---

### perf guardian

Install the Guardian agent skill and persona into a repository. Currently supports `install` for
the `claude` target only.

**Synopsis**

```
perf guardian install --target claude [--directory <repo>]
```

**Key flags**

| Flag | Type | Purpose |
|------|------|---------|
| `--target <claude>` | choice (required) | Coding agent to install for. Only `claude` is supported. |
| `--directory <path>` | dir path | Repository to install into. Must exist. Default: current directory. |

Writes `.claude/skills/android-performance/SKILL.md`, `.claude/agents/android-performance-guardian.md`
and the receipt `.guardian-manifest.json`. Installed and unchanged paths are listed on stdout.

Exit behavior: `0` on success, including a repeat install with nothing to change. `1` when a
destination file exists but is not owned by Guardian, or an owned file was modified since it was
installed (nothing is written; the cause is on stderr), on a filesystem error, or on a usage error
such as a missing or unsupported `--target` or a `--directory` that does not exist (Clikt usage
errors exit `1` for this command, not `2`). There is no `--force`.

**Example**

```bash
perf guardian install --target claude --directory ./my-app
```

---

### perf version

Print the installed version string. No flags.

**Example**

```bash
perf version
# output: 0.2.0
```

---

## Exit Code Contract

| Code | Meaning | Agent action |
|------|---------|--------------|
| `0` | Success. Command completed; all threshold gates and score gates passed or were not specified. For `perf analyze --output json`, valid JSON is on stdout. For `perf run`, the Markdown report was written to disk. | Parse stdout JSON (`analyze`) or read the report file (`run`). Proceed. |
| `1` | Runtime or gate failure. See triggers below. Human-readable cause is on stderr. For `perf analyze` threshold breach (`--max-main-thread-blocked-ms`), the JSON is STILL on stdout — it was written before the gate ran. | Read stderr for the cause. For `analyze` breaches, also parse stdout JSON. Fixing the underlying condition may allow a retry. |
| `2` | Usage or argument error (Clikt default). Bad flag, missing required argument, unknown subcommand, or a parent command invoked without a subcommand (e.g. `perf analyze` alone exits 2). | Do NOT retry with identical arguments — fix the invocation first. Retrying a exit-2 failure without changing arguments will always produce exit 2 again. |

**Exit-1 triggers (runtime or gate failure)**

- `trace_processor` binary not found on PATH.
- Trace file not found (argument to `perf analyze`).
- `--max-main-thread-blocked-ms` threshold exceeded (`perf analyze`).
- `--min-score` gate failed (`perf run`).
- Confirmed ANR, crash, or LMK event detected with `--exit-signals` active (`perf run`).
- ADB device missing or ambiguous (no device connected, or multiple devices with no `--serial`).
- `perf guardian install` collision: destination is unowned or was modified after install.

**Output stream rules**

1. **stderr carries the human-readable error message** for exit codes 1 and 2. stdout is not used
   for diagnostics.
2. **stdout is machine-readable only when `--output json` is explicitly specified** on
   `perf analyze`. All other commands write human-readable text or files.
3. **For `perf analyze --output json` with a threshold breach**: the JSON object is written to stdout
   FIRST, then the breach message is written to stderr, then exit 1 is raised. Agents MUST read
   stdout and stderr independently — do not assume stdout is empty on exit 1.
4. **Agents MUST NOT retry exit-2 failures without changing arguments.** Exit 2 is a static
   invocation error; the binary behavior will not change on retry.

---

## JSON Output Contract

Only `perf analyze <domain> <trace> --output json` emits machine-readable JSON to stdout.
`perf run` does NOT emit JSON in this version — it writes a Markdown file only.

### Schema

The JSON object matches `AnalyzeResult` as serialized by `kotlinx.serialization` (query pack `v1`):

```json
{
  "domain":           "rendering",
  "queryPackVersion": "v1",
  "tracePath":        "/abs/path/to/app.perfetto-trace",
  "metrics":          { "main_thread_blocked_ms": 42 },
  "rawOutputPreview": null
}
```

**Field types and semantics**

| Field | Type | Notes |
|-------|------|-------|
| `domain` | string | One of `startup`, `rendering`, `memory`, `cpu`. |
| `queryPackVersion` | string | Current version: `"v1"`. May change in future releases. |
| `tracePath` | string | Absolute path to the input trace file (resolved from the argument). |
| `metrics` | object | Map of metric name → JSON primitive (number, string) or null. |
| `rawOutputPreview` | string or null | First N lines of `trace_processor` output; `null` if not captured (default). |

**Critical agent guidance for `metrics`**

- The metric key set is domain-dependent and query-pack-version-dependent.
- Agents MUST NOT assume a fixed set of keys. Guard against absent or unknown keys.
- On a domain that has no relevant data in the trace, individual metric values may be `null`.
- The ONLY metric wired to a built-in exit gate is `main_thread_blocked_ms`
  (via `--max-main-thread-blocked-ms`). All other thresholds must be implemented on the agent side.

**Output format notes**

- The JSON object is written as a single line to stdout. There is no trailing newline guarantee.
- On a threshold breach (exit 1), the JSON is on stdout and the breach message is on stderr.
  Read stdout and stderr independently.

### Known metric keys (informational, query pack `v1`)

These keys are documented as informational. They reflect the current query pack and may change when
the query pack version advances. Agents MUST guard against any key being absent.

| Domain | Known metric keys |
|--------|-------------------|
| `rendering` | `main_thread_blocked_ms`, `gfx_slice_count`, `gfx_total_duration_ms` |
| `memory` | `process_count`, `max_rss_kb` |
| `cpu` | `top_slice_name`, `top_slice_duration_ms` |
| `startup` | domain-specific keys from `android_startup` run-metrics (structure is trace-dependent) |

---

## Agent Usage Scenarios

### Scenario 1 — Detect rendering regression in CI (exit gate)

**Goal:** Fail the CI pipeline when main-thread blocking time exceeds a budget.

**Preconditions:**
- A `.perfetto-trace` file produced by an earlier pipeline step.
- `trace_processor` on PATH.

**Command:**

```bash
perf analyze rendering ./app.perfetto-trace --output json --max-main-thread-blocked-ms 50
```

**Success path (exit 0):**
- Valid JSON on stdout.
- Read `metrics.main_thread_blocked_ms` to record the measured value.
- Pipeline proceeds.

**Failure path (exit 1 — threshold breach):**
- JSON is STILL on stdout (written before the breach check).
- Breach message on stderr: `Threshold breach: main_thread_blocked_ms=<N> > 50`.
- Agent surfaces the stderr message and fails the pipeline.
- Do NOT discard stdout — the JSON is parseable and contains the actual measured value.

---

### Scenario 2 — Analyze a startup trace offline

**Goal:** Extract startup metrics from a captured trace without a live device.

**Preconditions:**
- A `.perfetto-trace` file on disk.
- `trace_processor` installed and on PATH.

**Command:**

```bash
perf analyze startup ./startup.perfetto-trace --output json --package com.example.app
```

**Success path (exit 0):**
- JSON on stdout.
- Agent reads `metrics` object for startup timing values.
- `queryPackVersion` confirms the pack used.

**Failure path (exit 1):**
- If `trace_processor` is missing: stderr names the missing binary. Install it before retrying.
- If the trace file is not found: stderr contains the absolute path attempted. Verify the file path.

---

### Scenario 3 — Check app startup on a connected device

**Goal:** Measure cold-start latency on a live device and record the report as a CI artifact.

**Preconditions:**
- Android device connected (or AVD running). Verify with `adb devices`.
- App installed on the device.
- `perf init` completed (SDK configured).

**Command:**

```bash
perf run --package com.example.app --startup --duration 10 -o report.md
```

**Success path (exit 0):**
- Markdown report written to `report.md`.
- No JSON output from `perf run` in this version.
- Agent reads or archives `report.md` as a pipeline artifact.

**Failure path (exit 1):**
- No device connected or serial ambiguous: stderr describes the device error. Run `adb devices` to
  diagnose. Use `--serial` to specify if multiple devices are attached.
- Threshold breach (if `--min-score` or scenario thresholds are set): stderr names the breach.
- In all failure cases, stdout is not machine-readable (no JSON from `run`). Act on exit code + stderr.

---

### Scenario 4 — CPU analysis with an agent-side gate (optional)

**Goal:** Fail the pipeline when a CPU metric exceeds a domain-specific budget. There is no built-in
CPU threshold flag — the gate is implemented by the agent after parsing the JSON.

**Preconditions:**
- A `.perfetto-trace` file on disk.
- `trace_processor` on PATH.

**Command:**

```bash
perf analyze cpu ./trace.perfetto-trace --output json
```

**Success path (exit 0):**
- JSON on stdout.
- Agent reads `.metrics.top_slice_duration_ms` (informational key, query pack `v1`).
- Agent compares against its own budget and fails the pipeline if exceeded.
- This demonstrates the JSON-parsing pattern for domains without a built-in exit gate.

**Failure path (exit 1):**
- `trace_processor` missing or trace file not found — stderr describes the cause.
- Agent does NOT reach the gate check; fix the preconditions and retry.

---

## Roadmap / Out of Scope

The following capabilities are explicitly NOT available in this version of `perf`. Agents MUST NOT
attempt to invoke them.

- **MCP server** — `perf` does not expose an MCP endpoint. There is no long-lived server process,
  no JSON-RPC interface, and no MCP manifest. Deferred: MCP adds a long-lived process, protocol
  surface, and GraalVM packaging risk; reconsider once the JSON contract is stable.

- **`perf run --output json`** — `perf run` does NOT accept `--output json` and does NOT emit JSON
  to stdout. The `--output`/`-o` flag on `run` is the Markdown report file path only. Machine-readable
  output from `run` is a planned future increment.

- **NDJSON / streaming output** — no command emits newline-delimited JSON or streaming output. All
  output is either a single JSON object (`analyze --output json`) or a Markdown file.

- **Auto-publish to agent registries** — `perf` does not self-register with agent tool registries,
  MCP directories, or Claude Code skill manifests. Registration is manual.

---

## Verifying this document

All flag names and command examples in this document were audited against the corresponding
`perf <command> --help` output before this version was committed. Specific checks performed:

- `perf --help` — confirmed top-level commands: `init`, `sdk`, `run`, `analyze`, `history`, `version`.
- `perf run --help` — confirmed all flags in the Command Map table and Scenario 3 example.
- `perf analyze --help` and `perf analyze <domain> --help` — confirmed `--output`, `--package`,
  `--window-ms`, `--from-ts`, `--max-main-thread-blocked-ms`.
- `perf history --help` — confirmed `--package`, `--window`, `--history-dir`.
- `perf init --help` — confirmed `--install-sdk`, `--directory`, `--android-sdk`, `--avd`.
- Metric keys confirmed from `query-packs/v1/manifest.json` in the source tree.
- `queryPackVersion` value `"v1"` confirmed from `manifest.json` and `QueryPackLoaderTest`.

A CI doc-lint step (automated flag verification against `--help` output) is recommended as a
follow-up but is not in scope for this change (see R-6.4).
