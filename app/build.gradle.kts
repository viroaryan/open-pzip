plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.pzip"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "app.pzip"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            ndkBuild {
                arguments += listOf("APP_STL=c++_static", "APP_PLATFORM=android-26")
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    // One small APK per CPU type plus a universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystorePath = System.getenv("PZIP_KEYSTORE_PATH")
            val storePassword = System.getenv("PZIP_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("PZIP_KEY_ALIAS")
            val keyPassword = System.getenv("PZIP_KEY_PASSWORD")
            if (listOf(keystorePath, storePassword, keyAlias, keyPassword).all { !it.isNullOrBlank() }) {
                signingConfig = signingConfigs.create("production") {
                    storeFile = file(keystorePath!!)
                    this.storePassword = storePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/versions/**", "DebugProbesKt.bin")
        // Compressed native libs keep the download small (libVLC is the largest part).
        jniLibs.useLegacyPackaging = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // VLC engine: plays practically every video/audio codec, container and subtitle format.
    implementation("org.videolan.android:libvlc-all:3.7.7")

    // Archive engines (pure Java, shrunk by R8). RAR uses the bundled native UnRAR.
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.9")

    testImplementation("junit:junit:4.13.2")
}
