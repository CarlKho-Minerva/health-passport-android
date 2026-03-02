plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nexa.transform"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
