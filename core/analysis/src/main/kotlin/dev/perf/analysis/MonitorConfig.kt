package dev.perf.analysis

/**
 * Selects which monitoring engines run during a session.
 */
data class MonitorConfig(
    val strictMode: Boolean = true,
    val anrHints: Boolean = true,
    val rendering: Boolean = true,
    /** When true, `RunCommand` measures cold start (`am force-stop` + `am start -W`) before the timed session. */
    val startup: Boolean = false,
    /**
     * When true, issues `dumpsys batterystats --reset` at session start and parses
     * partial wake locks at end (destructive — clears system battery stats).
     */
    val wakelocks: Boolean = false,
    /**
     * When true, opens three concurrent logcat streams to detect confirmed ANR, crash, and LMK events.
     * Any confirmed exit is reported as CRITICAL and causes the CLI to exit non-zero.
     */
    val exitSignals: Boolean = false,
    /** When true, measures background network traffic via `dumpsys netstats --uid N`. INFO-only in v1. */
    val backgroundNetwork: Boolean = false,
    /** When true, measures background Wi-Fi scan count via `dumpsys batterystats`. INFO-only in v1. */
    val backgroundWifi: Boolean = false,
    /**
     * When true, diffs wakelock snapshots to detect stuck partial wake locks. Requires [wakelocks].
     * INFO-only in v1.
     */
    val stuckWakelocks: Boolean = false,
    /**
     * When true, opens a separate logcat stream filtered to `ActivityManager:W` and `PackageManager:W`
     * to detect permission denials for the target package's UID. INFO-only by default — does NOT
     * affect [dev.perf.analysis.PerfReport.hasIssues] or exit code unless [maxPermissionDenials] threshold is set.
     */
    val permissionDenials: Boolean = false,
)
