// Top-level build file. Common configuration for all sub-projects/modules lives here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// AGP adds Unified Test Platform configurations (host coverage/device-info/emulator-control) to
// every Android module, not just ones with real androidTest sources — confirmed via
// `./gradlew :app:dependencies`/`:baselineprofile:dependencies`, both resolve io.grpc:grpc-netty
// transitively through these, dragging in vulnerable Netty releases. Never shipped in the app,
// but flagged by Dependabot since it's in the resolved graph. Force every io.netty artifact
// (per-module, since UTP configs exist per-module) to a patched 4.1.x release; Netty keeps
// binary compatibility within the 4.1 line.
subprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "io.netty:netty-common:${libs.versions.netty.get()}",
                "io.netty:netty-buffer:${libs.versions.netty.get()}",
                "io.netty:netty-transport:${libs.versions.netty.get()}",
                "io.netty:netty-resolver:${libs.versions.netty.get()}",
                "io.netty:netty-codec:${libs.versions.netty.get()}",
                "io.netty:netty-codec-http:${libs.versions.netty.get()}",
                "io.netty:netty-codec-http2:${libs.versions.netty.get()}",
                "io.netty:netty-codec-socks:${libs.versions.netty.get()}",
                "io.netty:netty-handler:${libs.versions.netty.get()}",
                "io.netty:netty-handler-proxy:${libs.versions.netty.get()}",
                "io.netty:netty-transport-native-unix-common:${libs.versions.netty.get()}",
            )
        }
    }
}
