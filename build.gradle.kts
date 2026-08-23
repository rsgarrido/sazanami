import com.android.build.api.dsl.ApplicationExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Link the app module to CDPlaya's native audio decoder.
project(":app") {
    val nativeBuildScript = file("CMakeLists.txt")
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            externalNativeBuild {
                cmake {
                    path = nativeBuildScript
                }
            }
        }
    }
}
