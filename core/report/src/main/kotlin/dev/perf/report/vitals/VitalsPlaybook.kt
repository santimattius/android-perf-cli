package dev.perf.report.vitals

import dev.perf.analysis.model.PotentialAnrHintType
import dev.perf.analysis.model.StrictModeViolation

/**
 * Curated **static** links to official Android / Play guidance (no network I/O).
 * Complements optional `android docs search` output from the host CLI.
 */
object VitalsPlaybook {

    const val VITALS_HUB = "https://developer.android.com/topic/performance/vitals"

    fun coldStartSection(): List<String> = listOf(
        "### Guidance (Vitals: launch)",
        "- Reduce work before first frame: lazy init, `App Startup` library, avoid blocking main thread.",
        "- [App startup time](https://developer.android.com/topic/performance/vitals/launch-time)",
        "- [App quality overview](https://developer.android.com/guide/topics/android-vitals)",
        "",
    )

    fun renderingSection(): List<String> = listOf(
        "### Guidance (Vitals: slow rendering)",
        "- Investigate frame budgets, overdraw, and layout inflation on the main thread.",
        "- [Render vitals](https://developer.android.com/topic/performance/vitals/render)",
        "- [Inspect GPU rendering](https://developer.android.com/studio/profile/gpu-rendering)",
        "- [Vitals hub]($VITALS_HUB)",
        "",
    )

    fun memoryLeaksSection(): List<String> = listOf(
        "### Guidance (Vitals: memory / leaks)",
        "- Fix retained objects and release listeners; validate with LeakCanary in debug.",
        "- [Memory overview](https://developer.android.com/topic/performance/memory)",
        "- [Manage memory](https://developer.android.com/topic/performance/memory-management)",
        "- [Vitals hub]($VITALS_HUB)",
        "",
    )

    fun potentialAnrSection(hintTypes: Set<PotentialAnrHintType>): List<String> = buildList {
        add("### Guidance (Vitals: ANRs & responsiveness)")
        add("- Heuristic signals suggest main-thread pressure; confirm with traces and StrictMode.")
        add("- [ANRs](https://developer.android.com/topic/performance/vitals/anr)")
        add("- [Keep your app responsive](https://developer.android.com/topic/performance/vitals/anr#keep_your_app_responsive)")
        if (hintTypes.any { it == PotentialAnrHintType.CHOREOGRAPHER_SKIPPED_FRAMES || it == PotentialAnrHintType.HIGH_FRAME_TIME }) {
            add("- [Jank / frames](https://developer.android.com/topic/performance/vitals/render)")
        }
        if (hintTypes.contains(PotentialAnrHintType.LOOPER_SLOW_DISPATCH)) {
            add("- [Looper / Handler](https://developer.android.com/reference/android/os/Looper)")
        }
        add("- [Vitals hub]($VITALS_HUB)")
        add("")
    }

    fun strictModeSection(types: Set<StrictModeViolation.ViolationType>): List<String> = buildList {
        add("### Guidance (Vitals: StrictMode)")
        add("- Move disk and network work off the main thread (Dispatchers.IO, background workers).")
        add("- [StrictMode](https://developer.android.com/reference/android/os/StrictMode)")
        add("- [Excessive wakeups / I/O (Vitals)](https://developer.android.com/topic/performance/vitals)")
        if (types.contains(StrictModeViolation.ViolationType.NETWORK)) {
            add("- Keep network calls off the UI thread; see Vitals hub for related metrics.")
        }
        add("- [Vitals hub]($VITALS_HUB)")
        add("")
    }

    fun androidCliDocsIntro(): List<String> = listOf(
        "## Official docs (Android CLI)",
        "",
        "_Below is optional output from `android docs search` when the Android CLI is on your PATH._",
        "",
    )
}
