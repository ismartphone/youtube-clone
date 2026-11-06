// Module « core » du SDK du live partagé (ADR-031), compilé depuis le dépôt live-sdk cloné à côté
// du projet. Les sources sont les siennes, sans copie ; seul ce fichier de build est à nous, parce
// que celui du SDK vise AGP 8 et le plugin kotlin-android, incompatibles avec notre AGP 9.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val liveSdkDir: File = rootProject.file(providers.gradleProperty("liveSdkDir").get())

android {
    namespace = "fr.echimine.live.core"
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
    sourceSets {
        getByName("main") {
            kotlin.directories.add(liveSdkDir.resolve("kotlin/core/src/main/kotlin").path)
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
