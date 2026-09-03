plugins {
    id("com.android.library")
}

android {
    namespace = "com.vueo.shared.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // One pooled HTTP stack for shared content runtimes. Keep the version aligned
    // with the proven VUEO Mobile baseline while migration is in progress.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
}
