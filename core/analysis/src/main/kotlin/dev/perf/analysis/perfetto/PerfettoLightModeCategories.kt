package dev.perf.analysis.perfetto

internal object PerfettoLightModeCategories {

    /** Emulator-safe baseline atrace-backed tags (light mode). */
    val DEFAULT: Array<String> = arrayOf(
        "sched", "freq", "idle",
        "am", "wm", "gfx", "view",
        "input", "res", "dalvik",
    )

    /**
     * Additional tags merged for the `vitals` preset (some devices may reject them).
     */
    val VITALS_EXTRAS: Array<String> = arrayOf(
        "surfaceflinger",
    )
}

enum class PerfettoTracingPreset {
    DEFAULT,
    VITALS,
    CUSTOM,
}

fun resolveTracingPreset(presetLower: String, customCategoriesPresent: Boolean): PerfettoTracingPreset =
    when {
        customCategoriesPresent && presetLower == "custom" -> PerfettoTracingPreset.CUSTOM
        presetLower == "vitals" -> PerfettoTracingPreset.VITALS
        else -> PerfettoTracingPreset.DEFAULT
    }

internal fun categoriesForTracing(
    preset: PerfettoTracingPreset,
    customCategories: List<String>?,
): Array<String> =
    when {
        preset == PerfettoTracingPreset.CUSTOM &&
            customCategories != null &&
            customCategories.isNotEmpty() ->
            customCategories.distinct().toTypedArray()

        preset == PerfettoTracingPreset.VITALS ->
            PerfettoLightModeCategories.DEFAULT +
                PerfettoLightModeCategories.VITALS_EXTRAS

        else ->
            PerfettoLightModeCategories.DEFAULT
    }
