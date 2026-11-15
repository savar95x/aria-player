plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.savar.aria"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.savar.aria"
        minSdk = 31
        targetSdk = 35
        // CI run number keeps every build installable over the previous one.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        // 1.0.0 = first CI run after the bump; each later push is 1.0.1, 1.0.2, ... (see gradle.properties).
        val base = (project.findProperty("aria.versionBase") as String?)?.toIntOrNull() ?: 0
        val patch = (run - base).coerceAtLeast(0)
        versionName = "${project.findProperty("aria.version") ?: "1.0"}.$patch"
    }

    signingConfigs {
        // Shared key so CI builds and local builds update each other in place.
        // Swap for a private key (env vars) before publishing anywhere public.
        create("aria") {
            storeFile = file(System.getenv("ARIA_KEYSTORE") ?: "aria.keystore")
            storePassword = System.getenv("ARIA_KEYSTORE_PASSWORD") ?: "aria-player"
            keyAlias = System.getenv("ARIA_KEY_ALIAS") ?: "aria"
            keyPassword = System.getenv("ARIA_KEY_PASSWORD") ?: "aria-player"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("aria")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("aria")
        }
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.hls)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.datastore.preferences)
    implementation(libs.palette)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)
    implementation(libs.jaudiotagger)
}
