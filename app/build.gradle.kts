import java.util.Base64
import java.util.Properties
import java.net.URL
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Obfuscate the ScreenScraper *developer* API credentials so they're not sitting in the APK as
// plaintext (defeats a trivial `strings` grab). XOR + Base64; decoded at runtime by Secrets.kt with
// the SAME key. NOTE: this is obfuscation, not encryption — a determined reader of the (public)
// source can still reverse it. The only theft-proof option is a server-side proxy.
val ssObfKey = "e0r-ss-obf-2026"
fun obfuscateSecret(value: String): String {
    if (value.isEmpty()) return ""
    val k = ssObfKey.toByteArray(Charsets.UTF_8)
    val v = value.toByteArray(Charsets.UTF_8)
    val out = ByteArray(v.size) { i -> (v[i].toInt() xor k[i % k.size].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(out)
}

android {
    namespace = "com.gamelaunch.frontend"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.gamelaunch.frontend"
        // Android 6.0+. Old Android TV boxes are popular for retro emulation; APIs newer than 23 are
        // version-checked, and core-library desugaring backports java.time / newer collection methods.
        minSdk = 23
        targetSdk = 34
        versionCode = 51
        versionName = "3.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }

        // ScreenScraper dev credentials — set in local.properties, obfuscated into the APK (see
        // obfuscateSecret above) and decoded at runtime by Secrets.reveal().
        buildConfigField("String", "SS_DEV_ID",       "\"${obfuscateSecret((localProperties["SS_DEV_ID"] as String?) ?: "")}\"")
        buildConfigField("String", "SS_DEV_PASSWORD",  "\"${obfuscateSecret((localProperties["SS_DEV_PASSWORD"] as String?) ?: "")}\"")
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Two builds from one codebase: `full` (everything) and `lite` (a lighter runtime for weak
    // chipsets like the RG DS's RK3568). Same applicationId — a user installs whichever fits their
    // device; one replaces the other. The only difference is the LOW_POWER flag, which forces the
    // reduced-motion / delayed-video path on in the lite build (see PerformanceState).
    flavorDimensions += "power"
    productFlavors {
        create("full") {
            dimension = "power"
            buildConfigField("boolean", "LOW_POWER", "false")
        }
        create("lite") {
            dimension = "power"
            buildConfigField("boolean", "LOW_POWER", "true")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
        prefab = true
    }

    // The bundled Syncthing daemon (jniLibs/*/libsyncthing.so) must be extracted to a real file in
    // nativeLibraryDir so it can be exec'd — modern AGP otherwise keeps .so files compressed in the APK.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        // Bouncy Castle JARs contain duplicate Java 9 OSGi metadata unused by Android.
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }

    // The Syncthing daemon is fetched at build time (see the fetchSyncthing task) into this generated
    // jniLibs dir rather than committed to the repo.
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("syncthing-jni"))
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("banner-jni"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("banner-licenses"))

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // QR generation + camera scanning for Save Sync device pairing
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Web Transfer — embedded LAN HTTP server so a computer browser can push files to the device.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // Embedded Wireless ADB. These are bundled in eOr
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80.2")
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("io.github.vvb2060.ndk:boringssl:20250114")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.mockwebserver)
    // Real org.json on the unit-test classpath (Android's bundled one is a non-functional stub),
    // so profile.json (de)serialization can be tested on the JVM.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ── Save Sync: fetch the official Syncthing daemon at build time (not committed to git) ──
// Extracts libsyncthing.so from the official syncthing/syncthing-android release APK into a
// generated jniLibs dir. Keeps the ~26 MB binary out of the repo; provenance is explicit here.
val syncthingVersion = "1.28.1"
val syncthingJniDir = layout.buildDirectory.dir("syncthing-jni").get().asFile

val fetchSyncthing = tasks.register("fetchSyncthing") {
    val outDir = syncthingJniDir
    val url = "https://github.com/syncthing/syncthing-android/releases/download/$syncthingVersion/app-release.apk"
    val version = syncthingVersion
    val tmpDir = temporaryDir
    inputs.property("syncthingVersion", version)
    inputs.property("downloadUrl", url)
    outputs.dir(outDir)
    outputs.cacheIf("Pinned Syncthing release") { true }
    doLast {
        val outArm64 = File(outDir, "arm64-v8a/libsyncthing.so")
        val outArmeabiV7a = File(outDir, "armeabi-v7a/libsyncthing.so")
        val outArmeabi = File(outDir, "armeabi/libsyncthing.so")
        outArm64.parentFile.mkdirs()
        outArmeabiV7a.parentFile.mkdirs()
        outArmeabi.parentFile.mkdirs()
        val apk = File(tmpDir, "syncthing-android.apk")
        apk.parentFile.mkdirs()
        println("Fetching Syncthing $version daemon…")
        URL(url).openStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        ZipFile(apk).use { zip ->
            val entry64 = zip.getEntry("lib/arm64-v8a/libsyncthing.so")
                ?: error("arm64-v8a libsyncthing.so not found in $url")
            zip.getInputStream(entry64).use { input -> outArm64.outputStream().use { output -> input.copyTo(output) } }

            val entryArm = zip.getEntry("lib/armeabi/libsyncthing.so")
                ?: error("armeabi libsyncthing.so not found in $url")
            zip.getInputStream(entryArm).use { input -> outArmeabiV7a.outputStream().use { output -> input.copyTo(output) } }
            zip.getInputStream(entryArm).use { input -> outArmeabi.outputStream().use { output -> input.copyTo(output) } }
        }
        println("Syncthing daemon ready for ARM (arm64-v8a and armeabi-v7a/armeabi)")
    }
}

tasks.named("preBuild") { dependsOn(fetchSyncthing) }

// Compile the offscreen banner renderer into the APK; it runs entirely on the Android device.
val build3dsBannerRenderer = tasks.register<Exec>("build3dsBannerRenderer") {
    inputs.file(rootProject.file("rust-toolchain.toml"))
    inputs.dir("src/main/rust/three_ds_banner/src")
    inputs.file("src/main/rust/three_ds_banner/Cargo.toml")
    inputs.file("src/main/rust/three_ds_banner/Cargo.lock")
    inputs.file(rootProject.file("tools/build_3ds_banner.py"))
    inputs.file(rootProject.file("tools/banner_build_support.py"))
    outputs.dir(layout.buildDirectory.dir("banner-jni"))
    outputs.dir(layout.buildDirectory.dir("banner-licenses"))
    val python = providers.gradleProperty("eorPython")
        .orElse(providers.environmentVariable("EOR_PYTHON"))
        .getOrElse(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3")
    inputs.property("pythonExecutable", python)
    inputs.property("ndkVersion", android.ndkVersion)
    // These overrides change the compiler environment used by the Python build helper.
    for (name in listOf("EOR_CARGO", "CARGO_HOME", "RUSTUP_HOME", "RUSTUP_TOOLCHAIN",
        "CARGO_TARGET_DIR", "RUSTFLAGS", "CARGO_ENCODED_RUSTFLAGS",
        "CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS",
        "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_RUSTFLAGS",
        "CFLAGS_aarch64_linux_android", "CFLAGS_armv7_linux_androideabi")) {
        inputs.property(name, providers.environmentVariable(name).orElse(""))
    }
    commandLine(python, rootProject.file("tools/build_3ds_banner.py"),
        "--ndk", File(android.sdkDirectory, "ndk/26.1.10909125"),
        "--output", layout.buildDirectory.dir("banner-jni").get().asFile)
}
tasks.named("preBuild") { dependsOn(build3dsBannerRenderer) }
