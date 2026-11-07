plugins {
    id("com.android.library")
}

val gl19AudioTest = providers.gradleProperty("diplay.gl19AudioTest").getOrElse("baseline")
val gl19Release = providers.gradleProperty("diplay.gl19Release").orNull == "true"
require(gl19AudioTest in setOf("baseline", "a", "b", "aopus")) { "diplay.gl19AudioTest must be baseline, a, b or aopus" }
require(gl19AudioTest == "baseline" || providers.gradleProperty("diplay.gl19Diagnostics").orNull == "true") {
    "GL19 audio tests require -Pdiplay.gl19Diagnostics=true"
}

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 18
        buildConfigField("String", "GL19_AUDIO_TEST", "\"$gl19AudioTest\"")
        buildConfigField("boolean", "GL19_RELEASE", gl19Release.toString())
    }
    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":shared"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
}
