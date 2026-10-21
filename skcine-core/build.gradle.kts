// Pure Kotlin engine: math, interpolation, timelines, lifecycle. No server APIs, fully unit-testable.
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Engine micro-benchmark (not part of `build`): ./gradlew :skcine-core:benchmark
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs the SkCine core engine benchmark."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.dheaven.skcine.core.bench.CoreBenchmark")
}
