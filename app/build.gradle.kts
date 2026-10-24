plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.xacchuot.thu"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.xacchuot.thu"
        minSdk = 24          // Android 7.0 (Samsung S7 edge)
        targetSdk = 34
        versionCode = 7
        versionName = "0.7-sensitivity-modes"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Không nén file mô hình để máy đọc thẳng được từ bộ nhớ
    androidResources {
        noCompress.add("tflite")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-effects:$camerax")

    // Thư viện chạy mô hình AI (LiteRT, tên mới của TensorFlow Lite)
    implementation("com.google.ai.edge.litert:litert:2.3.0")

    val media3 = "1.10.1"
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
}
