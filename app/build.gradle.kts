// Copyright 2024-2026 Nexa AI, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.nexa.demo"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/healthpassport-release.jks")
            storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString() ?: ""
            keyAlias = "healthpassport"
            keyPassword = project.findProperty("KEY_PASSWORD")?.toString() ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.carlkho.healthpassport"
        minSdk = 27
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
//    sourceSets {
//        getByName("main") {
//            jniLibs.srcDirs("src/main/jniLibs")
//        }
//    }
    packagingOptions {
        jniLibs {
            // Compress .so files in the APK (safe for minSdk 27+).
            // With useLegacyPackaging=true the linker stores them UNCOMPRESSED, which makes the
            // per-device APK download count toward the 200 MB Play Store limit at full size.
            // Flipping to false compresses them (188 MB → ~75 MB in the APK).
            useLegacyPackaging = false
            // libstable-diffusion is part of the Nexa SDK AAR but is not used in this app.
            excludes += "**/libstable-diffusion.so"
            // HTP profiling/tracing readers are only needed for SDK development benchmarks.
            excludes += "**/libQnnHtpOptraceProfilingReader.so"
            excludes += "**/libQnnChrometraceProfilingReader.so"
            excludes += "**/libQnnHtpProfilingReader.so"
            excludes += "**/libQnnJsonProfilingReader.so"
            excludes += "**/libQnnLpaiProfilingReader.so"
            // Every libQnn*.so in lib/arm64-v8a is DUPLICATED in assets/npu/htp-files{,-v81}/.
            // The Nexa SDK loads QNN libs from the assets copies (extracts → writable dir → System.load),
            // never via System.loadLibrary from lib/. Excluding from jniLibs saves ~47 MB compressed.
            excludes += "**/libQnn*.so"
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
        buildConfig = true
    }
}

val bridgePathExist = gradle.extra["bridgePathExist"] as Boolean
print("bridgePathExist: $bridgePathExist\n")

// Strip Snapdragon 8 Elite (v85) NPU runtime from assets (~134 MB).
// htp-files-v85 targets sm8750 only. QDC device is sm8650 (Snapdragon 8 Gen 3 = v81),
// so v85 is never used and should not ship in the base module.
afterEvaluate {
    for (variantName in listOf("Release", "Debug")) {
        tasks.findByName("merge${variantName}Assets")?.doLast {
            outputs.files.forEach { dir ->
                // Strip Snapdragon 8 Elite (v85) HTP runtime (~134 MB, sm8750 only)
                File(dir, "npu/htp-files-v85").takeIf { it.exists() }?.deleteRecursively()
                    ?.also { println("Stripped npu/htp-files-v85 from $dir") }
                // Strip profiling/tracing reader libs from both HTP asset folders (~6 MB compressed)
                val profilingPatterns = listOf(
                    "libQnnHtpOptraceProfilingReader.so",
                    "libQnnChrometraceProfilingReader.so",
                    "libQnnHtpProfilingReader.so",
                    "libQnnJsonProfilingReader.so",
                    "libQnnLpaiProfilingReader.so"
                )
                for (htpDir in listOf("npu/htp-files", "npu/htp-files-v81")) {
                    for (lib in profilingPatterns) {
                        File(dir, "$htpDir/$lib").takeIf { it.exists() }?.delete()
                    }
                }
            }
        }
    }
}

dependencies {

    // ===== NEXA CLOUD SDK =====
    // Pinned to 0.0.24 (latest stable per docs.nexa.ai/en/nexa-sdk-android/quickstart)
    implementation("ai.nexa:core:0.0.24")
    // ===== NEXA CLOUD SDK END =====
    implementation(project(":transform"))
    implementation(":okdownload-core@aar")
    implementation(":okdownload-sqlite@aar")
    implementation(":okdownload-okhttp@aar")
    implementation(":okdownload-ktx@aar")
    implementation(kotlin("reflect"))
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.markwon.core)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.linkify)
    implementation(libs.recyclerview)
    implementation(libs.toaster)
    implementation(libs.material)
    implementation(libs.imm.bar)
    implementation(libs.imm.bar.ktx)
    implementation(libs.auto.size)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}