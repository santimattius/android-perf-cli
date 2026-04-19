plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.graalvm.native)
}

application {
    mainClass.set("dev.perf.cli.MainKt")
}

dependencies {
    implementation(project(":core:adb"))
    implementation(project(":core:analysis"))
    implementation(project(":core:report"))
    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Fat JAR para distribución como CLI
tasks.jar {
    archiveBaseName.set("perf-cli")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

graalvmNative {
    toolchainDetection.set(true)
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("perf")
            mainClass.set("dev.perf.cli.MainKt")
            buildArgs.add("--no-fallback")
        }
    }
}
