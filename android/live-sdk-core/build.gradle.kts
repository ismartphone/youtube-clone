// Module « core » du SDK du live partagé (ADR-031), compilé depuis le dépôt live-sdk cloné à côté
// du projet. Les sources sont les siennes, sans copie ; seul ce fichier de build est à nous, parce
// que celui du SDK vise AGP 8 et le plugin kotlin-android, incompatibles avec notre AGP 9.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val liveSdkDir: File = rootProject.file(providers.gradleProperty("liveSdkDir").get())

// Clone absent : arrêt immédiat avec la marche à suivre, plutôt que des erreurs de compilation
// incompréhensibles dans l'application (références fr.echimine introuvables).
if (!liveSdkDir.resolve("kotlin/core/src/main/kotlin").isDirectory) {
    throw GradleException(
        "SDK du live partagé introuvable dans ${liveSdkDir.absolutePath}. Cloner " +
            "https://github.com/echimine/live-sdk.git à côté du projet (dossier live-sdk, commit 9b2f639), " +
            "ou régler liveSdkDir dans android/gradle.properties. Voir le README, « Cloner le dépôt ».",
    )
}

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
