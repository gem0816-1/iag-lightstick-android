plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.iag.amppglass"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

dependencies {
    api("androidx.compose.animation:animation:1.12.0")
    api("androidx.compose.foundation:foundation:1.12.0")
    api("androidx.compose.ui:ui:1.12.0")
    api("androidx.compose.ui:ui-graphics:1.12.0")
    api("io.github.kyant0:shapes:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains:annotations:26.1.0")
    testImplementation("junit:junit:4.13.2")
}
