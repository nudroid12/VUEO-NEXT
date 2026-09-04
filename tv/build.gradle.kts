plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vueoCiVersionCode =
    System.getenv("VUEO_VERSION_CODE")
        ?.toIntOrNull()
val vueoTvCiVersionName =
    System.getenv("VUEO_TV_VERSION_NAME")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePath =
    System.getenv("VUEO_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePassword =
    System.getenv("VUEO_KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.vueo.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vueo.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = vueoCiVersionCode ?: 1
        versionName = vueoTvCiVersionName ?: "0.1.0"
    }

    signingConfigs {
        create("vueoRelease") {
            if (
                vueoKeystorePath != null &&
                vueoKeystorePassword != null
            ) {
                storeFile = file(vueoKeystorePath)
                storePassword = vueoKeystorePassword
                keyAlias = "vueo"
                keyPassword = vueoKeystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (
                vueoKeystorePath != null &&
                vueoKeystorePassword != null
            ) {
                signingConfig =
                    signingConfigs.getByName("vueoRelease")
            }
            // TV release APKs are distributed directly, so keep them lean.
            // R8 removes unreachable code while resource shrinking removes
            // resources made unused by minification.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    implementation(project(":shared:core"))
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")

}
