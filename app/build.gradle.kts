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

android {
    namespace = "com.gamelaunch.frontend"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gamelaunch.frontend"
        minSdk = 26
        targetSdk = 34
        versionCode = 28
        versionName = "1.8.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ScreenScraper dev credentials — set in local.properties
        buildConfigField("String", "SS_DEV_ID",       "\"${localProperties["SS_DEV_ID"] ?: ""}\"")
        buildConfigField("String", "SS_DEV_PASSWORD",  "\"${localProperties["SS_DEV_PASSWORD"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The bundled Syncthing daemon (jniLibs/*/libsyncthing.so) must be extracted to a real file in
    // nativeLibraryDir so it can be exec'd — modern AGP otherwise keeps .so files compressed in the APK.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // The Syncthing daemon is fetched at build time (see the fetchSyncthing task) into this generated
    // jniLibs dir rather than committed to the repo.
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("syncthing-jni"))

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

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.mockwebserver)
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
    // Capture everything into task-local vals at configuration time so the action closure holds no
    // references to script objects (required by the Gradle configuration cache).
    val out = File(syncthingJniDir, "arm64-v8a/libsyncthing.so")
    val url = "https://github.com/syncthing/syncthing-android/releases/download/$syncthingVersion/app-release.apk"
    val version = syncthingVersion
    val tmpDir = temporaryDir
    outputs.file(out)
    doLast {
        if (out.exists() && out.length() > 1_000_000L) return@doLast
        out.parentFile.mkdirs()
        val apk = File(tmpDir, "syncthing-android.apk")
        println("Fetching Syncthing $version daemon…")
        URL(url).openStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("lib/arm64-v8a/libsyncthing.so")
                ?: error("libsyncthing.so not found in $url")
            zip.getInputStream(entry).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
        }
        println("Syncthing daemon ready (${out.length()} bytes)")
    }
}

tasks.named("preBuild") { dependsOn(fetchSyncthing) }
