plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "app.plait"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.plait"
        minSdk = 26
        targetSdk = 36
        versionCode = 39
        versionName = "0.19.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Releases built by GitHub Actions are signed with the key from the repository's secrets (see
    // .github/workflows/release.yml). Local builds use this machine's debug key, as before.
    val releaseKeystore = System.getenv("PLAIT_KEYSTORE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("PLAIT_STORE_PASSWORD")
            keyAlias = System.getenv("PLAIT_KEY_ALIAS")
            keyPassword = System.getenv("PLAIT_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // A release build runs Compose far faster than a debug one. It is signed with the same key every time, so
            // it installs over earlier builds and keeps their data.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Installs the ahead-of-time profiles Compose ships, so scrolling and animation code is compiled, not interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    implementation("com.google.mediapipe:tasks-text:0.10.35")
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
