plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.example.cdplaya"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cdplaya"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)


    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation(libs.jaudiotagger)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    baselineProfile(project(":benchmark"))
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val performanceEnabled = providers
        .systemProperty("equalizer.performance")
        .orElse("false")
        .get()
    val longRunEnabled = providers
        .systemProperty("equalizer.longRun")
        .orElse("false")
        .get()
    val spotifyStress500k = providers
        .systemProperty("spotify.stress500k")
        .orElse("false")
        .get()
    val spotifyImportStress100k = providers
        .systemProperty("spotify.importStress100k")
        .orElse("false")
        .get()
    systemProperty(
        "equalizer.performance",
        performanceEnabled
    )
    systemProperty(
        "equalizer.longRun",
        longRunEnabled
    )
    systemProperty("spotify.stress500k", spotifyStress500k)
    systemProperty("spotify.importStress100k", spotifyImportStress100k)
    testLogging {
        showStandardStreams =
            performanceEnabled.toBoolean() ||
                    longRunEnabled.toBoolean()
    }
}