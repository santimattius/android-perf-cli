<!-- perf-guardian:begin v1 -->
## Android performance agents (installed by `perf guardian install`)

These Codex agents analyze Android performance with the `perf` CLI. Ask for them by name.

| Agent | Use when | Evidence |
|---|---|---|
| `android-performance-guardian` | the symptom is unclear or spans several areas | `perf analyze <domain> --output json` |
| `anr-auditor` | the app freezes or an ANR is reported | `perf run --exit-signals`, `perf analyze rendering` |
| `startup-auditor` | launch is slow or startup regressed | `perf run --startup`, `perf analyze startup` |
| `jank-auditor` | scrolling or animation stutters | `perf analyze rendering` |
| `leak-auditor` | memory grows or the app is killed in the background | `perf run --heap`, `perf analyze memory` |

Example: "have `jank-auditor` review the scroll trace in `./app.perfetto-trace`".
<!-- perf-guardian:end -->
