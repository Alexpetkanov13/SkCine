import proguard.gradle.ProGuardTask

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.7.0")
    }
}

plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":skcine-skript"))
    implementation(project(":skcine-platform"))
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly(libs.paper.api)
    compileOnly(libs.skript) {
        isTransitive = false
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Step 1: one jar with SkCine and its private, relocated Kotlin runtime.
tasks.shadowJar {
    archiveBaseName.set("SkCine")
    archiveClassifier.set("full")
    archiveVersion.set(project.version.toString())
    // Kotlin runtime is private to SkCine: relocate so it never clashes with other plugins.
    relocate("kotlin", "dev.dheaven.skcine.libs.kotlin")
    relocate("kotlinx", "dev.dheaven.skcine.libs.kotlinx")
    relocate("org.intellij.lang.annotations", "dev.dheaven.skcine.libs.annotations.intellij")
    relocate("org.jetbrains.annotations", "dev.dheaven.skcine.libs.annotations.jetbrains")
    exclude("META-INF/*.kotlin_module", "META-INF/versions/*/module-info.class", "module-info.class")
    exclude("META-INF/maven/**", "META-INF/proguard/**", "META-INF/com.android.tools/**", "DebugProbesKt.bin")
    mergeServiceFiles()
}

// Step 2: remove the parts of the bundled Kotlin runtime that SkCine never uses. Shrinking only:
// nothing is renamed or optimized, and every SkCine class is kept exactly as compiled.
val toolchainHome =
    javaToolchains
        .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .map { it.metadata.installationPath.asFile }

val shrinkJar = tasks.register<ProGuardTask>("shrinkJar") {
    group = "build"
    description = "Builds build/libs/SkCine-<version>.jar without unused Kotlin runtime classes."
    dependsOn(tasks.shadowJar)
    val full = tasks.shadowJar.flatMap { it.archiveFile }
    val out = rootProject.layout.buildDirectory.file("libs/SkCine-${project.version}.jar")
    inputs.file(full)
    outputs.file(out)

    injars(full)
    outjars(out)
    libraryjars(configurations.compileClasspath)
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        toolchainHome.map { File(it, "jmods") },
    )

    dontobfuscate()
    dontoptimize()
    dontwarn()
    dontnote()
    keepattributes("Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable,Exceptions")
    keep("class !dev.dheaven.skcine.libs.**,dev.dheaven.skcine.** { *; }")
    keepclassmembers("enum * { public static **[] values(); public static ** valueOf(java.lang.String); }")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.assemble {
    dependsOn(shrinkJar)
}
