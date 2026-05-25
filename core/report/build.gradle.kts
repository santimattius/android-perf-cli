plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:analysis"))
    implementation(project(":core:trace-analysis"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
