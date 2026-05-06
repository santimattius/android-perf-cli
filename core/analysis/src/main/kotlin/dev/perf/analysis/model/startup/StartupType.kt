package dev.perf.analysis.model.startup

import kotlinx.serialization.Serializable

@Serializable
enum class StartupType {
    COLD,
    WARM,
    HOT,
}
