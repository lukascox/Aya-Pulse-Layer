import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

val releaseKeystoreFile = signingProperty("ANDROID_KEYSTORE_FILE")
val hasReleaseSigningConfig = listOf(
    releaseKeystoreFile,
    signingProperty("ANDROID_KEYSTORE_PASSWORD"),
    signingProperty("ANDROID_KEY_ALIAS"),
    signingProperty("ANDROID_KEY_PASSWORD"),
).all { it != null }

android {
    namespace = "com.kei.pulse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kei.pulse"
        minSdk = 31
        targetSdk = 34
        // Upstream version, then this patch's own iteration on top of it:
        //   versionName = "<upstream>-aya.<n>"
        //   versionCode = <upstream code> * 100 + <n>
        // The name reads as Debian's upstream-revision convention. The code has to be an integer
        // and has to increase, or Android refuses the update -- so carrying upstream's 303 unchanged
        // would make two of our builds on the same upstream uninstallable over each other.
        // Bump <n> for every published release; reset it to 1 when upstream moves.
        versionCode = 30302
        versionName = "1.19.6-aya.2"

        // Stamped fresh on every build (unlike versionName/versionCode, which only move on a
        // published release) -- lets a pulled /sdcard session log
        // or an on-launch toast answer "is this actually the build with patch X" without guessing,
        // instead of relying on remembering to bump a version number by hand (STATUS.md, 2026-07-28,
        // after a suspected regression turned out to need this check first).
        buildConfigField(
            "String",
            "BUILD_TIMESTAMP",
            "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreFile!!)
                storePassword = signingProperty("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingProperty("ANDROID_KEY_ALIAS")
                keyPassword = signingProperty("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Quiet non-actionable advisories so lintDebug stays clean and meaningful:
        //  - dependency/SDK/Gradle "newer version available" — deps are deliberately pinned for stability
        //  - OldTargetApi — targetSdk 34 is intentional (see CLAUDE.md)
        //  - ObsoleteSdkInt — harmless dead version guards under minSdk 31
        //  - UseKtx (style) / DataExtractionRules (allowBackup=true is intentional)
        // Real-bug checks (UnusedResources, SetWorldReadable, DefaultLocale, …) stay ON.
        disable += setOf(
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion",
            "OldTargetApi", "ObsoleteSdkInt", "UseKtx", "DataExtractionRules",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Forward -Dreplay.logcat=<path> to the unit-test JVM so AutoTdpReplayTest.adHocReplayOfASharedCapture can
// replay an arbitrary PulseAutoTdp capture on demand. Only set when provided, so the test's assumeTrue(...)
// skips it otherwise.
tasks.withType<Test>().configureEach {
    System.getProperty("replay.logcat")?.let { systemProperty("replay.logcat", it) }
}
