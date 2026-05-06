package dev.perf.analysis.model.startup

import dev.perf.analysis.model.Severity
import kotlinx.serialization.Serializable

@Serializable
data class StartupReport(
    val cold: StartupStats? = null,
    val warm: StartupStats? = null,
    val hot: StartupStats? = null,
    val parsedOk: Boolean = true,
    val message: String = "",
) {
    val overallSeverity: Severity
        get() =
            listOfNotNull(cold?.severity, warm?.severity, hot?.severity)
                .minOrNull() ?: Severity.INFO

    fun forType(type: StartupType): StartupStats? =
        when (type) {
            StartupType.COLD -> cold
            StartupType.WARM -> warm
            StartupType.HOT -> hot
        }
}
