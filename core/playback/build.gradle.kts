plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pulsefin.core.playback"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        // Media3's practical API surface is marked @UnstableApi; this module's own compiler
        // opt-in (below) covers real Kotlin compilation, but AGP 9.2.1's lint pass under K2
        // doesn't honor it (verified: neither the module-wide -opt-in flag nor a per-file
        // @OptIn(UnstableApi::class) clears this check — both leave every finding in place, and
        // the latter even flags its own @OptIn declaration). Disabling rather than suppressing
        // per-call-site, since virtually every line in this module touches Media3's unstable API.
        disable += "UnsafeOptInUsageError"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // Media3's practical API surface (DownloadManager, SimpleCache, ExoPlayer.Builder's
        // chained setters, MediaSessionService internals, etc.) is marked @UnstableApi; this
        // module touches it pervasively enough that opting in per-declaration would be noise.
        freeCompilerArgs.add("-opt-in=androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Android Media3 (ExoPlayer) + MediaSession for deep system integration
    // (lock screen, Android Auto, Wear).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.database)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.kotlinx.coroutines.core)

    // Persists just enough of the live queue to restore it after process death.
    implementation(libs.androidx.datastore.preferences)
}
