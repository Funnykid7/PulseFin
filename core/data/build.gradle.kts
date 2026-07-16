plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pulsefin.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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

ksp {
    // Room schema snapshots per version, so future schema changes can ship real Migration
    // objects (diffed against these) instead of relying on the destructive-recreate fallback.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))

    // Official Jellyfin Kotlin SDK — wrapped behind repository interfaces (see JellyfinClientFactory).
    implementation(libs.jellyfin.core)

    // Room — single source of truth for the mirrored library.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Secure, persisted session/token storage.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.kotlinx.coroutines.core)
}
