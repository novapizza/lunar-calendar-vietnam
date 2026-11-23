import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Upload key for Google Play (Play App Signing keeps the app signing key), never committed: on a
// developer's machine apps/android/keystore.properties (storeFile, keyAlias, and storePassword,
// keyPassword or a passwordsFile holding them); in CI (.github/workflows/android-release.yml) the ANDROID_UPLOAD_* variables.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
// keystore.properties may leave the passwords to another file (passwordsFile=...), e.g. next to the .jks,
// so the file in the repository folder holds nothing secret.
fun loadProperties(f: File) = Properties().apply { f.inputStream().use { load(it) } }
val uploadKey: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> loadProperties(f).also { p -> p.getProperty("passwordsFile")?.let { p.putAll(loadProperties(file(it))) } } }
    ?: env("ANDROID_UPLOAD_STORE_FILE")?.let { file ->
        Properties().apply {
            setProperty("storeFile", file)
            setProperty("storePassword", env("ANDROID_UPLOAD_STORE_PASSWORD") ?: error("ANDROID_UPLOAD_STORE_PASSWORD is not set"))
            setProperty("keyAlias", env("ANDROID_UPLOAD_KEY_ALIAS") ?: error("ANDROID_UPLOAD_KEY_ALIAS is not set"))
            setProperty("keyPassword", env("ANDROID_UPLOAD_KEY_PASSWORD") ?: error("ANDROID_UPLOAD_KEY_PASSWORD is not set"))
        }
    }

android {
    // The code's package stays io.github.novapizza.licham; the application id is the Play identity, from the
    // app's domain lunar.coding4pizza.com. Permanent after the first Play upload.
    namespace = "io.github.novapizza.licham"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coding4pizza.lunar"
        minSdk = 26
        targetSdk = 36
        // CI sets both from the release tag (android-vX.Y.Z → X·10000 + Y·100 + Z); Play needs a higher
        // versionCode for every upload.
        versionCode = env("ANDROID_VERSION_CODE")?.toInt() ?: 1
        versionName = env("ANDROID_VERSION_NAME") ?: "0.1.0"
    }

    androidResources {
        localeFilters += listOf("vi")
    }

    signingConfigs {
        if (uploadKey != null) create("upload") {
            storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
            storePassword = uploadKey.getProperty("storePassword")
            keyAlias = uploadKey.getProperty("keyAlias")
            keyPassword = uploadKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Without keystore.properties the release build is signed with the debug key: runs on a device
            // for testing, and Play refuses it.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.window)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
