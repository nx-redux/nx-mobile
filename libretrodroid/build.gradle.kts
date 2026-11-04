import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.androidLibrary)
}

// LibretroDroid by Filippo Scognamiglio (Swordfish90), GPL-3.0. See README.md.

// The native deps are git submodules; without them CMake fails deep in add_subdirectory with no hint.
listOf(
    "src/main/cpp/oboe/CMakeLists.txt",
    "src/main/cpp/libretro/libretro-common/include/libretro.h",
    "src/main/cpp/rcheevos/include/rc_client.h",
    "src/main/cpp/libchdr/CMakeLists.txt",
).firstOrNull { !file(it).exists() }?.let {
    throw GradleException(
        "libretrodroid's native dependencies are git submodules ($it is missing): " +
            "run git submodule update --init --recursive"
    )
}
android {
    namespace = "com.swordfish.libretrodroid"
    compileSdk = 37
    // The NDK the cores are built with (cores/Makefile, release.yaml NDK_VERSION); AGP's default moves with AGP.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // Available arguments are inside ${SDK}/cmake/.../android.toolchain.cmake file
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}
