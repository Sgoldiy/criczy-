import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Firebase (fan community) is configured by app/google-services.json, which is never committed.
// Without it (CI, fresh clones) the plugin is skipped and the community shows as unavailable.
// Debug builds use the id com.criczy.app.debug. Until that app is also registered in Firebase
// (and the file downloaded again), debug builds skip Firebase and show no community.
val googleServices = file("google-services.json")
if (googleServices.exists()) {
    apply(plugin = "com.google.gms.google-services")
    // Crash reports (Crashlytics) also upload the release build's R8 mapping, so stack traces are readable.
    apply(plugin = "com.google.firebase.crashlytics")
    if (!googleServices.readText().contains("\"com.criczy.app.debug\"")) {
        tasks.matching { it.name == "processDebugGoogleServices" }.configureEach { enabled = false }
    }
}

// Release signing is read from keystore.properties, which is never committed.
// Without it, release builds are produced unsigned-by-you (debug-signed) for local testing only.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// The AdMob app id must be in the manifest at build time; ad unit ids come from Firebase Realtime Database
// at run time. Put admob.appId=ca-app-pub-…~… in admob.properties (never committed). Without it, builds use
// Google's sample app id, which only serves test ads: fine for development and CI, never for Play.
val admobProperties = Properties().apply {
    val file = rootProject.file("admob.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val admobAppId: String = admobProperties.getProperty("admob.appId") ?: "ca-app-pub-3940256099942544~3347511713"

android {
    namespace = "com.criczy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.criczy.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] = admobAppId
        // Crash reports from release builds only; debug crashes stay on the developer's machine.
        manifestPlaceholders["crashReports"] = "true"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["crashReports"] = "false"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is used throughout the models; this makes it work on Android 7 (API 24-25).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Play Store screenshots are only rendered when asked for with -Pstore=true.
                it.systemProperty("criczy.store", providers.gradleProperty("store").getOrElse("false"))
            }
        }
    }
    // UI tests render screens with the API responses captured for the network module's tests.
    sourceSets["test"].resources.srcDir("../core/network/src/test/resources")
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
}

// UI tests need the debug-only test activity, so they run against the debug variant only.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}
