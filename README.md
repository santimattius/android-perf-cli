# Android Perf CLI

A **command-line tool for Android app performance monitoring** on real devices and emulators. It collects signals such as **StrictMode violations**, **janky frames / frozen frames**, and optional **heap analysis** (via [Shark](https://square.github.io/leakcanary/shark/)), then writes a **Markdown report** and prints a concise summary in the terminal.

Designed for **local debugging** and **CI**: scenario files can define **thresholds**; breaches exit with a **non-zero status code** so pipelines fail when performance regresses.

---

## Features

| Area | What it does |
|------|----------------|
| **ADB workflow** | Resolves devices, can install an APK, launches the app, and runs a timed monitoring session. |
| **Rendering** | Captures frame metrics (e.g. janky / frozen frames) for analysis and reporting. |
| **StrictMode** | Surfaces disk/network-on-main-thread and other StrictMode issues from logcat. |
| **ANR hints (heuristic)** | Optional logcat-based signals (e.g. skipped frames, slow dispatch) that suggest **main-thread pressure** — not a substitute for real ANR reproduction. |
| **Perfetto (optional)** | Records a system trace when the device has `perfetto`; pull to host and open in [Perfetto UI](https://ui.perfetto.dev). |
| **Heap (optional)** | When enabled, triggers a heap dump and analyzes it with Shark (debuggable builds / permissions permitting). |
| **Reports** | `report.md` (and console output) summarize findings; scenario mode supports CI-friendly thresholds. |
| **SDK setup** | `perf sdk install` can bootstrap **platform-tools** (adb) when no Android SDK is configured. |

---

## Requirements

- **JDK 17+**
- **Android SDK** with **platform-tools** (`adb`) on the host, *or* use built-in **`perf sdk install`** (see [INSTALL.md](INSTALL.md)).

No standalone Gradle installation is required for development—the repo ships the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

---

## Quick start

```bash
# Build the fat JAR (from a clone)
./gradlew :app:jar

# Run the CLI (adjust path to match gradle.properties version)
java -jar app/build/libs/perf-cli-0.1.0.jar --help
```

Configure the Android SDK path (interactive), or install command-line tools + `adb` in one step:

```bash
perf init
# or
perf sdk install
```

Run a short session against a package (device or emulator must be connected):

```bash
adb devices
perf run --package com.example.app.debug --duration 30
```

Output includes a **`report.md`** in the current directory by default (`-o` to change).

---

## Commands

| Command | Purpose |
|---------|---------|
| `perf init` | Save Android SDK path (and optional default AVD) to `~/.config/perf-cli/config.json`. |
| `perf init --install-sdk` | Install SDK command-line tools + **platform-tools** then save config. |
| `perf sdk install` | Same SDK bootstrap as above, focused on `adb`. |
| `perf run` | Monitor the app, collect metrics, write the report. |

Use `perf run --help` for flags such as `--scenario`, `--heap`, `--serial`, `--apk`, `--output`, `--project-dir`, `--perfetto`, `--startup`, `--activity`, `--skip-android-docs`, `--skip-emulator`, and `--emulator-headless`.

---

## CI and scenarios

Define a JSON scenario with engines (StrictMode, rendering, heap, **anrHints**, **perfetto**) and **thresholds** (including **maxPotentialAnrHints**). See [scenarios/example-scenario.json](scenarios/example-scenario.json) and [INSTALL.md](INSTALL.md). Run:

```bash
perf run --package com.example.app.debug --scenario scenarios/example-scenario.json
```

If thresholds are exceeded, the process **exits with code 1**, which CI systems treat as a failure.

---

## Installation (binary & packaging)

- **From source:** [INSTALL.md](INSTALL.md) (alias `perf`, `scripts/install-perf.sh`, troubleshooting).
- **Releases, Homebrew template, native image:** [DISTRIBUTION.md](DISTRIBUTION.md).

When you push a **`v*`** tag, [`.github/workflows/release.yml`](.github/workflows/release.yml) (**Release & distribution**) publishes a GitHub Release with the fat **`perf-cli-<version>.jar`** and checksums, bumps the in-repo Homebrew formula, and best-effort attaches a **Linux native** binary to the same release for cURL installs (see [DISTRIBUTION.md](DISTRIBUTION.md)).

---

## Development

```bash
./gradlew test           # all modules
./gradlew :app:jar       # fat JAR
./gradlew :app:nativeCompile   # GraalVM native image (requires GraalVM + native-image)
```

### Project layout

| Module | Role |
|--------|------|
| `:app` | CLI entrypoint (Clikt), commands, config |
| `:core:adb` | ADB abstraction |
| `:core:analysis` | Monitoring, logcat parsing, optional Shark heap analysis |
| `:core:report` | Console + Markdown reporting |

---

## Documentation

| Doc | Content |
|-----|---------|
| [INSTALL.md](INSTALL.md) | Detailed setup, SDK bootstrap, smoke tests |
| [DISTRIBUTION.md](DISTRIBUTION.md) | Releases, Homebrew, GraalVM |

---

## Contributing

Issues and pull requests are welcome. Please run **`./gradlew test`** before submitting changes.

---

## Acknowledgements

- [Shark / LeakCanary](https://github.com/square/leakcanary) (Square) for heap analysis
- [Clikt](https://github.com/ajalt/clikt) and [Mordant](https://github.com/ajalt/mordant) for the CLI UX
