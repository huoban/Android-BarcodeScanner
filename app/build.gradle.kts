plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

import com.android.build.api.variant.FilterConfiguration
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

val versionPropsFile = file("${rootProject.projectDir}/version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
}
val currentVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt()
val currentVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.1"
val parts = currentVersionName.split(".")
val major = parts.getOrElse(0) { "1" }
val minor = parts.getOrElse(1) { "0" }
val patch = (parts.getOrElse(2) { "1" }.toInt() + 1)
val newVersionName = "$major.$minor.$patch"
val newVersionCode = currentVersionCode + 1

android {
    namespace = "com.example.barcodescanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.barcodescanner"
        minSdk = 28
        targetSdk = 35
        versionCode = newVersionCode
        versionName = newVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val version = variant.versionName
            val abi = variant.outputs.first().filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI.name }?.identifier ?: "universal"
            output.outputFileName = "app-${abi}-${variant.buildType.name}_${version}.apk"
        }
        variant.preBuildProvider.get().doFirst {
            versionProps.setProperty("VERSION_CODE", newVersionCode.toString())
            versionProps.setProperty("VERSION_NAME", newVersionName)
            versionPropsFile.writer().use { versionProps.store(it, null) }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("com.google.android.material:material:1.11.0")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")

    implementation("com.caverock:androidsvg-aar:1.4")

    implementation("com.google.android.gms:play-services-oss-licenses:17.0.1")
}
