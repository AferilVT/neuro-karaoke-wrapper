plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.soul.neurokaraoke.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark / baseline-profile generation requires an API 28+ device.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // The app module this test suite drives and profiles.
    targetProjectPath = ":app"
}

// Generates against whatever device/emulator is attached (fast, local + CI-with-emulator).
// For fully reproducible headless runs, swap to a Gradle Managed Device instead:
//   testOptions.managedDevices + baselineProfile { managedDevices += "…"; useConnectedDevices = false }
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
