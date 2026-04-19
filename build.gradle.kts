plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.graalvm.native) apply false
}

allprojects {
    group = "dev.perf.cli"
    version = findProperty("version") as String? ?: "0.1.0"
}
