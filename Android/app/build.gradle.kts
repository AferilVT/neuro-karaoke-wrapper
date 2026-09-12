plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

// Load signing properties from local.properties
import java.util.Properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    // Code/R/BuildConfig package. Kept as-is so no source files move.
    namespace = "com.soul.neurokaraoke"
    compileSdk = 36

    defaultConfig {
        // Play Store application id (registered on the Play Console dashboard).
        // Differs from `namespace` on purpose — AGP handles the distinction.
        applicationId = "com.neurokaraoke"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GitHub repo for update checks
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"aferilvt\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"neuro-karaoke-wrapper\"")

        // Self-updater toggle. Defaults on (GitHub channel). The Play Store build
        // passes -PenableUpdater=false so no self-update path ships (Play policy).
        val updaterEnabled = (project.findProperty("enableUpdater") ?: "true").toString().toBoolean()
        buildConfigField("boolean", "ENABLE_UPDATER", "$updaterEnabled")
    }

    flavorDimensions += "platform"
    productFlavors {
        create("mobile") {
            dimension = "platform"
            // Default applicationId
        }
        create("automotive") {
            dimension = "platform"
            // For AAOS, we might want a different suffix or just keep it same if it's a different track
            // Usually it's the same package name but different distribution.
        }
    }

    signingConfigs {
        create("release") {
            // CI: environment variables (set by GitHub Actions)
            // Local: local.properties
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
                ?: localProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)

    // Compose for TV (focus-native surfaces for the Android TV 10-foot UI)
    implementation(libs.androidx.tv.material)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil:2.6.0")

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation("androidx.media:media:1.7.0")

    // Lifecycle ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutine ↔ Guava ListenableFuture bridge (for MediaLibraryService callbacks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    // Baseline profile installer — installs the bundled ART profile on app launch
    // so ART can AOT-compile hot paths without a slow cold-start verification pass.
    // 1.4.x required to install profiles on API 35+ devices (1.3.1 doesn't support them).
    implementation(libs.androidx.profileinstaller)

    // Consumes the ART profile generated by the :baselineprofile module and merges it
    // into src/main/baseline-prof.txt for release builds. Run ./gradlew :app:generateReleaseBaselineProfile
    baselineProfile(project(":baselineprofile"))

    // Android for Cars App Library — custom AA browse UI (tabs, grids, lists)
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")

    // Testing
    testImplementation(libs.junit)
    // Real org.json on the JVM test classpath — the android.jar stub throws "not mocked".
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}