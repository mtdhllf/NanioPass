plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cc.nanio.autofilltest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cc.nanio.autofilltest"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
