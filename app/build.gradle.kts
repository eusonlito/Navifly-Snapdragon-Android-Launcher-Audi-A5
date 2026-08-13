plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildTimeEpochMillis = System.currentTimeMillis()

android {
    namespace = "com.lito.a5launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lito.a5launcher"
        // This project targets the documented Android 14 head unit. Keeping the
        // minimum aligned with that device lets the app use the platform locale
        // and storage contracts without legacy compatibility branches.
        minSdk = 34
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"
        buildConfigField("long", "BUILD_TIME_EPOCH_MS", "${buildTimeEpochMillis}L")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // The real-trip JSONL remains outside the app sources, but is packaged
            // as an asset only in debug APKs.
            buildConfigField("boolean", "TELEMETRY_REPLAY_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // The production APK targets the Navifly Snapdragon 685 only.
            // Debug keeps every ABI so it remains installable in the emulator.
            ndk {
                abiFilters += "arm64-v8a"
            }
            buildConfigField("boolean", "TELEMETRY_REPLAY_ENABLED", "false")
            // Installable test-production artifact. A private distribution key can
            // replace this signing config later without changing the optimized build.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    sourceSets {
        // Resolve from the repository root. A path relative to this module is
        // ambiguous in AGP and previously allowed debug APKs without a replay.
        getByName("debug").assets.directories.add(rootProject.file("debug").absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // These dependency binaries are distributed already stripped. Asking
            // AGP to strip them again only emits a misleading production warning.
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libmaplibre.so",
            )
        }
    }
    lint {
        // Production is intentionally built only for the ARM64 Navifly head unit.
        disable += "ChromeOsAbiSupport"
        // MapLibre brings Timber transitively; Android Log remains the deliberate
        // diagnostics API so logs stay available through logcat on the head unit.
        disable += "LogNotTimber"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("org.maplibre.gl:android-sdk:13.4.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}
