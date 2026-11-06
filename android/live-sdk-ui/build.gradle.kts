// Module « ui » du SDK du live partagé (ADR-031) : écrans Compose, compilés depuis le dépôt live-sdk
// cloné. Même principe que live-sdk-core : sources du SDK, fichier de build à nous (AGP 9).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val liveSdkDir: File = rootProject.file(providers.gradleProperty("liveSdkDir").get())
val uiDir: File = liveSdkDir.resolve("kotlin/ui/src/main")

android {
    namespace = "fr.echimine.live.ui"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            kotlin.directories.add(uiDir.resolve("kotlin").path)
            res.directories.add(uiDir.resolve("res").path)
            manifest.srcFile(uiDir.resolve("AndroidManifest.xml"))
        }
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("fr.echimine.live.core.InternalLiveSdkApi")
    }
}

dependencies {
    api(project(":live-sdk-core"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.livekit.android)
}
