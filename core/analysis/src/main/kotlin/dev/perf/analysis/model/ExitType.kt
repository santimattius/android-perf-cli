package dev.perf.analysis.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExitType { ANR, CRASH, LMK }
