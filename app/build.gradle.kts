import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}

// Real release signing, read from a gitignored keystore.properties (see docs/RELEASE_SIGNING.md)
// or environment variables — never from anything committed. Absent either, release falls back
// to debug signing so local builds keep working without a keystore.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigningProperty(propertyKey: String, envVar: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(envVar)

val releaseStoreFile = releaseSigningProperty("storeFile", "RELEASE_STORE_FILE")

android {
    namespace = "com.pulsefin.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pulsefin.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = (project.findProperty("APP_VERSION_CODE") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("APP_VERSION_NAME") as? String) ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseSigningProperty("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningProperty("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningProperty("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Real signing when keystore.properties/env vars are set (see docs/RELEASE_SIGNING.md);
            // otherwise debug-signed so the optimized build still installs on local test devices.
            if (releaseStoreFile == null) {
                logger.warn(
                    "⚠️ No release keystore configured — assembleRelease will be signed " +
                        "with the DEBUG key, not suitable for distribution. See docs/RELEASE_SIGNING.md."
                )
            }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

composeCompiler {
    // Treat the immutable domain models (and the List snapshots we pass around) as stable
    // so list rows can be skipped during scroll instead of recomposing every pass.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
}

baselineProfile {
    // Skip automatic generation during local builds to keep build times fast.
    // Profiles should be generated manually when needed.
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:playback"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)

    // Baseline Profile: ships the AOT profile in the APK; the :baselineprofile module generates it.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Per-screen Monet: extract a seed from album art (Palette) and build a tonal scheme.
    implementation(libs.androidx.palette.ktx)
    implementation(libs.materialkolor.color.utilities)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
