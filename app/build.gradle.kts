import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pinder.airhand"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pinder.airhand"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

val handModel = layout.projectDirectory.file("src/main/assets/hand_landmarker.task")
tasks.register("downloadHandLandmarkerModel") {
    outputs.file(handModel)
    doLast {
        val out = handModel.asFile
        if (!out.exists()) {
            out.parentFile.mkdirs()
            URI("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task")
                .toURL().openStream().use { input: java.io.InputStream ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
        }
    }
}
tasks.named("preBuild").configure { dependsOn("downloadHandLandmarkerModel") }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    // Google's current official Hand Landmarker Android sample uses 0.10.29.
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
}
