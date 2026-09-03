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
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.4.0")

    // Shared coroutine primitives used by networking, provider sync and runtime concurrency.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Shared local JavaScript provider runtime used by both Mobile and TV.
    implementation("io.github.dokar3:quickjs-kt:1.0.14")

    // Kept in shared core for the provider compatibility bridge migration.
    implementation("org.jsoup:jsoup:1.23.2")
}
