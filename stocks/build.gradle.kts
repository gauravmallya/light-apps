import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String

        // Alpaca API credentials live in local.properties (gitignored). They're read at build
        // time and exposed via BuildConfig so the API layer can attach them at request time —
        // never committed, and fall back to empty string so a missing local.properties doesn't
        // break the build (the app just shows a "not configured" state).
        val alpacaApiKey = PropertyReader.local(project, "alpacaApiKey")
            ?: System.getenv("ALPACA_API_KEY")
        val alpacaSecret = PropertyReader.local(project, "alpacaSecret")
            ?: System.getenv("ALPACA_SECRET")
        val alpacaEndpoint = PropertyReader.local(project, "alpacaEndpoint")
            ?: System.getenv("ALPACA_ENDPOINT")
            ?: ""

        buildConfigField("String", "ALPACA_API_KEY", "\"${alpacaApiKey.escapeBuildConfigString()}\"")
        buildConfigField("String", "ALPACA_SECRET", "\"${alpacaSecret.escapeBuildConfigString()}\"")
        buildConfigField("String", "ALPACA_ENDPOINT", "\"${alpacaEndpoint.escapeBuildConfigString()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

/** Build-script helper for pulling small local build config out of local.properties. */
object PropertyReader {
    fun local(project: org.gradle.api.Project, key: String): String? {
        val file = project.rootProject.file("local.properties")
        if (!file.exists()) return null
        val props = Properties()
        file.inputStream().use { props.load(it) }
        return props.getProperty(key)?.takeIf { it.isNotBlank() }
    }
}

private fun String.escapeBuildConfigString(): String =
    // The endpoint was stored with surrounding quotes in local.properties.
    removeSurrounding("\"")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client"))
    testImplementation(libs.kotlin.test)
}