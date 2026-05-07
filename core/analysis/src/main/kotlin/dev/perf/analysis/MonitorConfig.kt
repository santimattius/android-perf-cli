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
)
