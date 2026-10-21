// Skript language layer: types, effects, expressions, conditions, events, sections and structures.
dependencies {
    implementation(project(":skcine-platform"))
    compileOnly(libs.paper.api)
    compileOnly(libs.skript) {
        isTransitive = false
    }
}
