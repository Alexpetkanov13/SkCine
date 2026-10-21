plugins {
    alias(libs.plugins.kotlin.serialization)
}

// Paper implementations: cameras, actors, displays, particles, sound, dialogue, player state.
dependencies {
    api(project(":skcine-api"))
    compileOnly(libs.paper.api)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.launcher)
}
