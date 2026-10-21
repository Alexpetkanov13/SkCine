// Public integration API for other plugins. Not required for authoring scenes (that happens in Skript).
dependencies {
    api(project(":skcine-core"))
    compileOnly(libs.paper.api)
}
