package dev.perf.analysis.perfetto

/**
 * Data sources for `adb shell perfetto` in **light mode** (see
 * [Android Perfetto](https://developer.android.com/tools/perfetto#syntax)).
 *
 * Design decision: keep a **small conservative** set of `atrace`-style tags
 * that work across API levels. Exotic tags (e.g. some `ftrace` paths) were
 * removed after reports of `perfetto` exiting non-zero on common emulators.
 */
internal object PerfettoLightModeCategories {

    /** Ordered list passed after `--buffer` and before `--out`. */
    val DEFAULT: Array<String> = arrayOf(
        "sched", "freq", "idle",
        "am", "wm", "gfx", "view",
        "input", "res", "dalvik",
    )
}
