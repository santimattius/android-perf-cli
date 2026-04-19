# Android Perf CLI — Installation

For **prebuilt binaries, Homebrew, and GraalVM native image**, see [DISTRIBUTION.md](DISTRIBUTION.md).

## Requirements

| Item | Minimum | Check |
|------|---------|-------|
| JDK | 17 | `java -version` |
| Android SDK + `platform-tools` (ADB) | Any recent version | `adb version` |

You do **not** need **Gradle installed globally**: the repo includes the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) (`./gradlew`).

### Install the SDK from perf-cli (without Android Studio)

If you do not have `adb` or an SDK in the usual locations:

```bash
perf sdk install
```

This downloads Google’s *command-line tools* (if needed), accepts licenses, and installs **platform-tools** (includes `adb`) under the default SDK path (e.g. `~/Library/Android/sdk` on macOS). It saves configuration automatically.

Alternatives: `perf init --install-sdk` (same flow plus init options), or [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android sdk install "platform-tools"` — the package id does not use `;latest` like some `sdkmanager` examples).

`perf run` tries to **detect** a valid SDK (`ANDROID_HOME`, typical paths) and saves config without requiring a manual `perf init`.

---

## Quick path (from the repo)

```bash
./scripts/install-perf.sh
```

This builds the fat JAR and prints an `alias perf='java -jar …'` you can paste into `~/.zshrc` or `~/.bashrc`.

---

## Build manually

```bash
./gradlew :app:jar
```

Output (version comes from `gradle.properties`):

```
app/build/libs/perf-cli-0.1.0.jar
```

It is a **fat JAR** (dependencies embedded); no external classpath is required.

---

## `perf` alias

**Current shell only:**

```bash
alias perf="java -jar $(pwd)/app/build/libs/perf-cli-*.jar"
```

**Persistent** (adjust the absolute path to your clone and the versioned JAR name):

```bash
echo 'alias perf="java -jar /path/to/repo/app/build/libs/perf-cli-0.1.0.jar"' >> ~/.zshrc
source ~/.zshrc
```

**Verify:** `perf --help`

---

## Initial setup (once per machine)

```bash
perf init
```

Writes `~/.config/perf-cli/config.json` (SDK path and default AVD when applicable).

---

## Quick test with a device or emulator

```bash
adb devices
perf run --package com.example.app.debug --duration 10
```

### JSON scenario (e.g. CI)

Use a scenario file and point `perf run` at it:

```bash
perf run --package com.example.app.debug --scenario scenarios/example-scenario.json
```

If any **threshold** is breached, the process exits with code **1** (CI-friendly).

#### Scenario fields

| Key | Meaning |
|-----|---------|
| `name` | Label for logs and reports (required). |
| `durationSeconds` | How long to run monitoring (default **30**). |
| `engines` | Which collectors run (all default to **true** except **heap** and **perfetto** — see below). |
| `thresholds` | Limits; exceeding them escalates the run to a failing exit code. |

**Engines** (`engines` object):

| Key | Default | Role |
|-----|---------|------|
| `strictMode` | `true` | StrictMode violations from logcat. |
| `rendering` | `true` | Janky / frozen frame metrics. |
| `heap` | `false` | Heap dump + Shark analysis at end of session. |
| `anrHints` | `true` | Heuristic “potential ANR risk” lines from logcat (skipped frames, slow dispatch, etc.) — **not** a guarantee of ANR. |
| `perfetto` | `false` | Record a **Perfetto** system trace for the whole session (requires `perfetto` on the device). |

**Thresholds** (`thresholds` object):

| Key | Default | Role |
|-----|---------|------|
| `maxJankyPercent` | `5.0` | Max acceptable janky frame percentage. |
| `maxFrozenFrames` | `0` | Max frozen frames. |
| `maxStrictModeViolations` | `0` | Max StrictMode violations. |
| `maxPotentialAnrHints` | `50` | Max count of **WARNING**-or-higher ANR *hint* events (see `anrHints`). |

A full example is in [scenarios/example-scenario.json](scenarios/example-scenario.json).

#### Extra `perf run` flags

| Flag | Purpose |
|------|---------|
| `--project-dir <dir>` | Android project root used to build **clickable source links** in the report (if omitted, the tool tries to infer from the current directory). |
| `--perfetto` | Record a Perfetto trace for the session (same idea as `engines.perfetto: true`; either enables tracing). Trace is pulled to the host; open it in [Perfetto UI](https://ui.perfetto.dev). |
| `--skip-emulator` | Do **not** auto-start the **default AVD** from `perf init` when no device is connected. |
| `--emulator-headless` | When an AVD is auto-launched, pass `-no-window` (useful in CI). |

Auto-emulator: configure `defaultAvd` with `perf init` so `perf run` can start an emulator if nothing is connected (unless `--skip-emulator`).

### Heap (Shark)

```bash
perf run --package com.example.app.debug --duration 30 --heap
```

Requires write access to `/data/local/tmp` on the device and a **debuggable** build variant when applicable.

---

## Project tests

```bash
./gradlew test
./gradlew :core:analysis:test
```

HTML reports: `<module>/build/reports/tests/test/index.html`

---

## Report structure (`report.md`)

Sections appear when there is something to show (issues, hints, or optional captures):

```markdown
# Perf Report — `com.example.app.debug`

| Field | Value |
|-------|-------|
| Package | `com.example.app.debug` |
| Date | 2026-04-18 21:30:00 ART |
| Overall Severity | CRITICAL |

## Memory Leaks

### CRITICAL `com.example.app.ui.HomeFragment`

- **Retained heap**: ~128 KB
- **Shortest path**: `Activity → Fragment → Runnable`

## StrictMode Violations
…

## Potential ANR hints (heuristic)

… logcat-derived signals such as Choreographer skipped frames or slow main-thread dispatch — **indicators of risk**, not proof of an ANR.

## Perfetto

Path to the pulled `.perfetto-trace` file (if recording succeeded), or a short message if tracing was skipped (e.g. binary missing on device).
```

---

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| `adb not found at …/platform-tools/adb` | SDK path in `~/.config/perf-cli/config.json` or run `perf init` again. |
| `Process not found for package` | Wrong package or app not installed: `adb shell pm list packages \| grep …` |
| `No ADB devices` | `adb devices`; wait for boot; or set `defaultAvd` in config so `perf run` can launch an emulator (unless `--skip-emulator`). |
| Multiple devices | `perf run … --serial emulator-5554` |
| Perfetto skipped / missing trace | Device image must include the `perfetto` binary; physical devices and recent emulators usually work. Use `--perfetto` or `"perfetto": true` in the scenario. |
| No source links in report | Pass `--project-dir` to the app module or repo root that contains `src/`. |
