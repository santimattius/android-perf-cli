# Android Perf CLI

A **command-line tool for Android app performance monitoring** on real devices and emulators. It
collects signals such as **StrictMode violations**, **janky / frozen frames**, **optional startup
timings** (cold / warm / hot with TTID/TTFD from logcat where available, plus legacy **`am start -W`
** cold totals), heuristic **ANR-risk hints** from logcat, optional **heap analysis
** ([Shark](https://square.github.io/leakcanary/shark/)), optional **Perfetto** system traces (with
**`default`** / **`vitals`** presets and graceful fallback), optional **Gradle-assembled APKs**, and
optional **automated workflows** (deeplinks, input, screenshots). It writes a **Markdown report**
with **ISO-8601 timestamps**, **Android Vitals**–oriented guidance links, and prints a concise
summary in the terminal.

Designed for **local debugging** and **CI**: JSON **scenarios** can define **`profile": "full"`** or
**`--full`** for a heavy bundled run; **thresholds** make the process exit **`1`** on breach so
pipelines fail when performance regresses.

---

## Features

| Area                      | What it does                                                                                                                                                                                                                                                                                                                                                                                                                           |
|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ADB workflow**          | Resolves devices, can install an APK or **assemble** one via Gradle (`--project-dir`, `--build-variant`), launches the app, runs a timed session.                                                                                                                                                                                                                                                                                      |
| **Rendering**             | `dumpsys gfxinfo` frame stats (janky / frozen frames, percentiles).                                                                                                                                                                                                                                                                                                                                                                    |
| **StrictMode**            | Disk/network-on-main-thread and other StrictMode issues from logcat (package PID scoped).                                                                                                                                                                                                                                                                                                                                              |
| **ANR hints (heuristic)** | Logcat-based signals (e.g. skipped frames, slow dispatch) — **not** a substitute for Vitals ANR rates or real ANRs.                                                                                                                                                                                                                                                                                                                    |
| **Startup**               | When **`engines.startup`** is on: multi-type sampling via **`startupTypes`** (e.g. cold/warm/hot), **`startupSamples`**, and logcat-backed TTID/TTFD aggregation. Scenario thresholds **`maxColdStartMs`** (cold P50), **`maxWarmStartMs`**, **`maxHotStartMs`**. Legacy **`coldStart`** on the report remains for backward compatibility.                                                                                             |
| **Wake locks (optional)** | Opt-in partial wake locks via **`dumpsys batterystats`** (see **`--wakelocks`** and scenario **`engines.wakelocks`**). Runs a destructive **`batterystats --reset`** at session start (clears **system-wide** battery stats). Thresholds **`maxWakelockTimeMs`** / **`maxWakelockCount`**; a **`## Wake Locks`** table in the Markdown report. **`--full`** / **`"profile":"full"`** turns wake locks on with the other heavy engines. |
| **Perfetto (optional)**   | Presets **`default`**, **`vitals`** (+fallback), or **`custom`** categories; coverage **`session`** or **`startup`**. Opens in [Perfetto UI](https://ui.perfetto.dev). Offline **`perf analyze`** (startup/rendering/memory/cpu) via host `trace_processor` — see [docs/perfetto-investigation.md](docs/perfetto-investigation.md).                                                                                                    |
| **Heap (optional)**       | Heap dump end-of-session + Shark analysis (debuggable builds / permissions permitting).                                                                                                                                                                                                                                                                                                                                                |
| **Process snapshot**      | Optional **`dumpsys meminfo`** excerpt + thread count (`/proc/.../status`).                                                                                                                                                                                                                                                                                                                                                            |
| **Screenshots**           | `adb exec-out screencap -p` into an **artifacts** directory (next to `-o` or `--artifacts-dir`).                                                                                                                                                                                                                                                                                                                                       |
| **Workflow**              | Scenario JSON **`workflow`** steps: wait, deeplink, keyevents, tap/swipe, gfx reset, screenshots.                                                                                                                                                                                                                                                                                                                                      |
| **Full profile**          | **`--full`** or **`"profile":"full"`** merges heavy engines (heap, Perfetto, startup), Vitals preset behaviour, snapshots, screenshots — see **[docs/perf-cli-run.md](docs/perf-cli-run.md)**.                                                                                                                                                                                                                                         |
| **Reports**               | `report.md` with timestamps, findings, Perfetto paths, Vitals playbook links; optional **`android docs search`** snippet. CI: scenario **thresholds** → exit **`1`** on breach.                                                                                                                                                                                                                                                        |
| **SDK setup**             | `perf sdk install` bootstraps **platform-tools** (`adb`) when no SDK is configured.                                                                                                                                                                                                                                                                                                                                                    |

---

## Requirements

- **JDK 17+**
- **Android SDK** with **platform-tools** (`adb`) on the host, *or* use **`perf sdk install`** (
  see [INSTALL.md](INSTALL.md)).

No standalone Gradle installation is required for developing this repo — it ships
the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

---

## Quick start

```bash
# Build the fat JAR (from a clone)
./gradlew :app:jar

# Help (adjust JAR filename to match gradle.properties / build output)
java -jar app/build/libs/perf-cli-*.jar --help
java -jar app/build/libs/perf-cli-*.jar run --help
```

Configure the Android SDK path, or install command-line tools + `adb` in one step:

```bash
perf init
# or
perf sdk install
```

Run a short session (device or emulator):

```bash
adb devices
perf run --package com.example.app.debug --duration 30
```

Run a **full** bundled profile (`--scenario` omitted → 120s default monitor window):

```bash
perf run --full --package com.example.app.debug -o report.md
```

Use a scenario
file ([example](scenarios/example-scenario.json), [full profile example](scenarios/example-full.json)):

```bash
perf run --package com.example.app.debug --scenario scenarios/example-full.json
```

Output defaults to **`report.md`**; use **`-o`**. Artefacts (screenshots, copied traces) default to
**`perf-artifacts/`** beside the report or **`--artifacts-dir`**.

---

## Commands

| Command                   | Purpose                                                                           |
|---------------------------|-----------------------------------------------------------------------------------|
| `perf init`               | Save Android SDK path (optional default AVD) to `~/.config/perf-cli/config.json`. |
| `perf init --install-sdk` | Install SDK CLI tools + **platform-tools** then save config.                      |
| `perf sdk install`        | Same bootstrap, SDK-focused (`adb`).                                              |
| `perf run`                | Monitor, collect metrics, write the report (see **`perf run --help`**).           |
| `perf guardian install`   | Install the Android performance skill and agent persona into a repo (Claude).     |

### Guardian: agent skill and persona

`perf guardian install --target <claude|codex> [--directory <repo>]` writes agent skills and
personas into a repository so a coding agent can run `perf` and interpret its output.

| Skill | Persona | Focus |
|-------|---------|-------|
| `android-performance` | `android-performance-guardian` | General: startup, rendering, memory, CPU. |
| `android-anr-triage` | `anr-auditor` | ANRs and main-thread stalls. |
| `android-startup-budget` | `startup-auditor` | Startup time against a budget. |
| `android-jank-review` | `jank-auditor` | UI jank and dropped frames. |
| `android-memory-leak-review` | `leak-auditor` | Memory growth, leaks, low-memory kills. |

| Target | Skills | Personas | Other |
|--------|--------|----------|-------|
| `claude` | `.claude/skills/<id>/SKILL.md` | `.claude/agents/<id>.md` | |
| `codex` | `.agents/skills/<id>/SKILL.md` | `.codex/agents/<id>.toml` | A marked block in `AGENTS.md` |

Guardian also writes `.guardian-manifest.json`, a receipt of what it owns with content hashes.
Installing a second target keeps the first one's files owned.

- `--target` is required and takes one value per run (`claude` or `codex`). `--directory` defaults
  to the current directory and must already exist.
- Installed and unchanged paths are printed on stdout. Errors go to stderr with exit code `1`.
- Re-running is a no-op when nothing changed.
- Guardian never overwrites a file it does not own or one you edited after installing: it aborts
  with exit `1` and changes nothing. There is no force or backup mode yet.
- For Codex, Guardian only touches the region between its `<!-- perf-guardian:begin v1 -->` and
  `<!-- perf-guardian:end -->` markers in `AGENTS.md`. Everything else in the file is preserved
  byte for byte. If you edit inside the markers, or damage them, the install aborts.

Notable **`perf run`** flags (see **`run --help`** and [INSTALL.md](INSTALL.md) for the full
matrix):

| Flag / pattern                                       | Purpose                                                                                                                                                                                                                                   |
|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--scenario path.json`                               | Engines, thresholds, profile, Perfetto preset/coverage, workflow, build block, snapshots.                                                                                                                                                 |
| `--full`                                             | Heavy CLI-only profile without a scenario file (heap + Perfetto + startup + snapshots + screenshots; longer default duration).                                                                                                            |
| `-d` / `--duration`                                  | Monitoring seconds (ignored when `--scenario` sets `durationSeconds`).                                                                                                                                                                    |
| `--project-dir`                                      | Android Gradle project root (source links, optional Gradle assemble).                                                                                                                                                                     |
| `--build-variant debug\|release`                     | With `--project-dir`, run `assemble*` before install (mutually exclusive with `--apk`).                                                                                                                                                   |
| `--apk`                                              | Install this APK instead of Gradle output.                                                                                                                                                                                                |
| `--perfetto`, `--startup`, `--activity`, `--heap`    | Shortcut toggles when not using JSON overrides.                                                                                                                                                                                           |
| `--wakelocks`                                        | When **no** `--scenario` is used, enables wake-lock capture (otherwise set **`"engines": { "wakelocks": true }`** in JSON). Implies **`dumpsys batterystats --reset`** at the start of the monitor window — see **Wake locks** row above. |
| `--artifacts-dir`                                    | Directory for screenshots and pulled Perfetto files.                                                                                                                                                                                      |
| `--serial`, `--skip-emulator`, `--emulator-headless` | Device / emulator control.                                                                                                                                                                                                                |

---

## CI and scenarios

Define engines (including **`startup`**, **`startupTypes`**, **`startupSamples`**), optional *
*`profile`** / Perfetto keys, **`workflow`**, **`build`**, and **thresholds** (including *
*`maxColdStartMs`**, **`maxWarmStartMs`**, **`maxHotStartMs`**) in JSON. Optional wake-lock keys: *
*`engines.wakelocks`**, **`thresholds.maxWakelockTimeMs`**, **`thresholds.maxWakelockCount`** —
see [example-scenario.json](scenarios/example-scenario.json)
and [example-full.json](scenarios/example-full.json).

```bash
perf run --package com.example.app.debug --scenario scenarios/example-scenario.json
```

For a bundled **full-health** scenario:

```bash
perf run --package com.example.app.debug --scenario scenarios/example-full.json
```

If thresholds are exceeded, the process exits with code **`1`**.

---

## Agent / CI Usage

Using `perf` from an AI agent or CI loop? See [docs/SKILL.md](docs/SKILL.md) — the agent-facing
skill doc: command map, exit-code contract, and the `analyze --output json` schema.

---

## Installation (binary & packaging)

- **From source:** [INSTALL.md](INSTALL.md) (`perf` alias, `scripts/install-perf.sh`,
  troubleshooting).
- **Releases, Homebrew, native image:** [DISTRIBUTION.md](DISTRIBUTION.md).

Pushing a **`v*`** tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml) (
fat JAR, checksums, Homebrew formula bump, Linux native artifact when GraalVM is available).

---

## Development

```bash
./gradlew test           # all modules
./gradlew :app:jar       # fat JAR
./gradlew :app:nativeCompile   # GraalVM native image (needs GraalVM + native-image)
```

### Project layout

| Module           | Role                                                               |
|------------------|--------------------------------------------------------------------|
| `:app`           | CLI (Clikt), commands, Gradle runner, workflow, scenario loading   |
| `:core:adb`      | `adb` / `exec-out` abstraction                                     |
| `:core:analysis` | Watchdog, logcat, gfxinfo, Perfetto, startup, proc snapshot, Shark |
| `:core:report`   | Markdown + console reporters, Vitals playbook                      |

---

## Documentation

| Doc                                              | Content                                                                                      |
|--------------------------------------------------|----------------------------------------------------------------------------------------------|
| [README.md](README.md)                           | This overview                                                                                |
| **[docs/perf-cli-run.md](docs/perf-cli-run.md)** | **`perf run`** concepts: full profile, Perfetto presets, workflow steps, artefacts, CI notes |
| [INSTALL.md](INSTALL.md)                         | Setup, **`perf run`** flag/scenario tables, Perfetto heap tips                               |
| [DISTRIBUTION.md](DISTRIBUTION.md)               | Releases, packaging                                                                          |

In Cursor, an optional `.cursor/skills/android-perf-cli/` workspace skill can help triage `perf`
workflows.

---

## Contributing

Issues and pull requests are welcome. Please run **`./gradlew test`** before submitting changes.

---

## Acknowledgements

- [Shark / LeakCanary](https://github.com/square/leakcanary) (Square) — heap analysis
- [Clikt](https://github.com/ajalt/clikt), [Mordant](https://github.com/ajalt/mordant) — CLI UX
