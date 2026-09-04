plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vueoCiVersionCode =
    System.getenv("VUEO_VERSION_CODE")
        ?.toIntOrNull()
val vueoCiVersionName =
    System.getenv("VUEO_VERSION_NAME")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePath =
    System.getenv("VUEO_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePassword =
    System.getenv("VUEO_KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.vueo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vueo.app"
        minSdk = 23
        targetSdk = 36
        versionCode = vueoCiVersionCode ?: 23
        versionName = vueoCiVersionName ?: "0.9.6"
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

            // Release APKs are distributed directly, so keep the package lean.
            // R8 removes unused Compose/icon/dependency code and resource shrinking
            // removes resources that become unreachable after minification.
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
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared:core"))

    // Compose 1.11.x line, compatible with compileSdk 36.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")

    // Mobile still directly references OkHttp request/client types in its
    // update/compatibility facade. QuickJS, DNS-over-HTTPS and Jsoup are owned
    // by :shared:core and must not be declared a second time here.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
}
