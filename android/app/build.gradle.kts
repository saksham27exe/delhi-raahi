import java.util.Properties
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firebase sign-in is switched on by app/google-services.json (git-ignored). Without it the app
// builds as before and uses the backend's own phone + password accounts.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Machine-local values (git-ignored): backend address. Never committed.
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Release signing key (git-ignored): android/keystore.properties -> storeFile, storePassword, keyAlias, keyPassword.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.delhiraahi"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.delhiraahi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Phones and tablets (ARM) plus the usual 64-bit emulator; old 32-bit x86 emulators are left out.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        buildConfigField("String", "API_BASE", "\"${local.getProperty("api.base", "http://10.0.2.2:8010")}\"")
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) create("release") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".dev" }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }   // unsigned if there is no key
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true   // compress native libraries (map + wake word): much smaller download
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/wakeModel"))
}

// "Hey Raahi" wake word: the open-source Vosk small Indian-English model (Apache-2.0, ~37 MB).
// Downloaded once at build time into build/ (not committed), then packed into the APK so the
// wake word works offline.
val wakeModel = "vosk-model-small-en-in-0.4"
val fetchWakeModel by tasks.registering {
    val out = layout.buildDirectory.dir("generated/wakeModel/wake")
    outputs.dir(out)
    doLast {
        val root = out.get().asFile
        if (File(root, "$wakeModel/am/final.mdl").exists()) return@doLast
        root.deleteRecursively(); root.mkdirs()
        val url = URI("https://alphacephei.com/vosk/models/$wakeModel.zip").toURL()
        ZipInputStream(url.openStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val f = File(root, e.name)
                require(f.canonicalPath.startsWith(root.canonicalPath)) { "bad zip entry ${e.name}" }
                if (e.isDirectory) f.mkdirs() else { f.parentFile.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
            }
        }
        check(File(root, "$wakeModel/am/final.mdl").exists()) { "wake model download incomplete" }
    }
}
tasks.named("preBuild") { dependsOn(fetchWakeModel) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Map: MapLibre Native (open source) with OpenStreetMap-based styles; no API key.
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    // QR codes for demo tickets
    implementation("com.google.zxing:core:3.5.3")
    // Offline wake word ("Hey Raahi"): Vosk speech recognition, open source, no key, runs on the phone
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
