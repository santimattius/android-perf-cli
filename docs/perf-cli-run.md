# `perf run` reference

This document describes how **`perf run`** collects metrics and produces **`report.md`**. Flag-level tables also appear in **[INSTALL.md](../INSTALL.md)**.

## Goals

- **Local profiling** on a USB device or emulator: StrictMode, frames (gfxinfo), heuristic ANR hints, optional cold start, heap/Shark, optional Perfetto traces.
- **CI gates** via **JSON scenarios** + **thresholds** (exit code **1** on breach).

Offline metrics are **signals** aligned with **[Android Vitals](https://developer.android.com/topic/performance/vitals)** topics (startup, slow rendering, ANR). They **do not** replace aggregated Play Console rates.

---

## Basic invocation

```bash
perf run --package com.example.app.debug --duration 30
```

Requires `adb` access to exactly one device (or `--serial`), or configured **default AVD** (see `perf init`).

---

## Full / heavy profile

### CLI without a scenario file

```bash
perf run --full --package com.example.app.debug -o report.md
```

- Enables **heap**, **Perfetto** (Vitalse-oriented preset unless overridden), **startup** (cold start timing).
- Enables **proc snapshot** (`dumpsys meminfo` excerpt + thread count) and **screenshots** (start/end and workflow screenshots).
- Default monitoring duration becomes **120s** when no `--scenario` is used (CLI-only `--full`).
- Artefacts (PNG, copied `.perfetto-trace`) land under `./perf-artifacts/` next to `-o`, or **`--artifacts-dir`**.

### Scenario file

See **[scenarios/example-full.json](../scenarios/example-full.json)** — set `"profile": "full"` and tune `durationSeconds`, `engines`, and `thresholds`.

Resolution logic lives in **`resolveRunParameters`** ([ScenarioResolution.kt](../app/src/main/kotlin/dev/perf/cli/scenario/ScenarioResolution.kt)): full profile merges in heavy engines; explicit JSON fields override where applicable.

---

## Perfetto presets

| Preset (`perfettoPreset`) | Behaviour |
|---------------------------|-----------|
| `default` | Conservative light-mode categories (scheduler, gfx, input, dalvik…). |
| `vitals` | Adds **`surfaceflinger`** on top of default; **falls back** to `default` if the device rejects extra tags. |
| `custom` | Uses **`perfettoCategories`** — you supply the light-mode category names documented for `adb shell perfetto`. |

**Coverage:**

- **`session`** — trace length aligns with the monitoring window (scaled to at least a few seconds).
- **`startup`** — shorter capped trace geared toward startup analysis alongside cold-start measurement.

Traces open in **[Perfetto UI](https://ui.perfetto.dev)**; the JVM does **not** parse protobuf.

---

## Optional Gradle assemble

Build an APK before install when you pass **`--project-dir`** and either:

- **`--build-variant debug`** (or **`release`**), or
- Scenario block **`build": { "enabled": true, "variant": "…", "module": ":app" }`**

Implementation: **`dev.perf.cli.gradle.GradleBuildRunner`** — invokes `./gradlew :module:assemble<Variant>`, picks the newest APK under `module/build/outputs/apk`.

**Do not combine** **`--apk`** with an assemble path for the same run.

---

## Workflow steps (scenario `workflow`)

Each element is a JSON object with **`type`** (case-insensitive):

| Type | Fields | Notes |
|------|--------|--------|
| `wait` | `seconds` (number) | Sleep before next step. |
| `deeplink` / `intent` | `uri` | `am start` VIEW intent (`-d`). |
| `keyevent` / `key` | `key` | e.g. `BACK`, `HOME`, or numeric keycode string. |
| `tap` | `x`, `y` | `adb shell input tap`. |
| `swipe` | `x`, `y`, `x2`, `y2`, optional `durationMs` | Swipe gesture. |
| `gfx_reset` | — | `dumpsys gfxinfo … reset`. |
| `screenshot` | optional `label` | PNG via **`adb exec-out screencap -p`**, path listed in report. |

Workflow runs after the brief **monkey** launch and before the long monitoring window.

---

## Report contents

Generated Markdown includes:

- **Timestamps**: report generation (**ISO-8601 UTC**) and **monitoring window** (start → end).
- **Session metadata**: scenario label, whether **full mode** ran.
- **Issue sections** (when applicable): thresholds, StrictMode, ANR hints, rendering, leaks, cold start.
- **Sidecar sections** when collected: **process snapshot**, **screenshots**, **Perfetto** path (+ optional degradation note).
- Static **Android Vitals**–oriented guidance links (**[VitalsPlaybook](../core/report/src/main/kotlin/dev/perf/report/vitals/VitalsPlaybook.kt)**), plus optional **`android docs search`** output if the Android CLI is on `PATH`.

---

## Examples

Smallest smoke test:

```bash
perf run -p com.example.debug -d 15
```

CI with thresholds:

```bash
perf run -p com.example.debug --scenario scenarios/example-scenario.json -o artifacts/perf-report.md
```

Full profile from scenario file:

```bash
perf run -p com.example.debug --scenario scenarios/example-full.json --project-dir /path/to/android/project
```

With local assemble + Perfetto-heavy run (adapt paths):

```bash
perf run --full -p com.example.debug --project-dir . --build-variant debug \
  --artifacts-dir ./perf-out -o ./perf-out/report.md
```

---

## Troubleshooting pointers

| Symptom | See |
|---------|-----|
| No `adb` / SDK | [INSTALL.md](../INSTALL.md) `perf sdk install`, `ANDROID_HOME`. |
| Perfetto skipped | Trace message in report — device/emulator image may lack `perfetto`; try a physical device or newer system image. |
| Gradle assemble fails | Release signing must be configured in the app project — the CLI does not manage keystores. |
| Screenshots empty | Screenshots disabled unless **`--full`**, scenario **`screenshots: true`**, or profile full merges them on. |

For complete flag and JSON field matrices, prefer **[INSTALL.md](../INSTALL.md)**.
