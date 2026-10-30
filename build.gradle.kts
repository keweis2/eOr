import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatformFactory
import org.gradle.platform.OperatingSystem

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Explicit vendor URLs let Gradle provision Java before it evaluates settings/plugins.
// Regenerate the committed criteria with ./gradlew updateDaemonJvm after changing these.
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(25)
    val base = "https://cdn.azul.com/zulu/bin/zulu25.38.109-ca-jdk25.0.5.0.3-"
    toolchainDownloadUrls.set(mapOf(
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.LINUX) to uri("${base}linux_x64.tar.gz"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.LINUX) to uri("${base}linux_aarch64.tar.gz"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.MAC_OS) to uri("${base}macosx_x64.tar.gz"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.MAC_OS) to uri("${base}macosx_aarch64.tar.gz"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.WINDOWS) to uri("${base}win_x64.zip"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.WINDOWS) to uri("${base}win_aarch64.zip"),
    ))
}
