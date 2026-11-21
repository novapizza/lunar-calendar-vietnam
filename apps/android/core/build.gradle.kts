import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: lunar calendar, mệnh, day facts, colour tokens. No Android dependency,
// so it is unit-tested on the JVM and can back the widgets too.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

val shared = rootProject.layout.projectDirectory.dir("../../shared")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// The JSON in shared/ is the single source of truth: packaged as Java resources, never copied by hand.
sourceSets {
    main {
        resources.srcDir(shared.dir("data"))
        resources.srcDir(shared.dir("design"))
    }
}
tasks.processResources {
    exclude("**/*.svg")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("testVectors", shared.dir("test-vectors").asFile.absolutePath)
    inputs.dir(shared.dir("test-vectors"))
}
