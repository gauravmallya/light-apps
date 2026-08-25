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

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String

        // This tool talks to a personal, self-hosted backend (see README for setup).
        // The base URL and admin token are personal/secret and must never be committed.
        // They're read from local.properties (gitignored) at build time and exposed via
        // BuildConfig. If unset, the app builds fine but shows a "not configured" state.
        val baseUrl = PropertyReader.local(project, "locationApproverBaseUrl")
            ?: System.getenv("LOCATION_APPROVER_BASE_URL")
            ?: ""
        val adminToken = PropertyReader.local(project, "locationApproverAdminToken")
            ?: System.getenv("LOCATION_APPROVER_ADMIN_TOKEN")
            ?: ""

        buildConfigField("String", "BASE_URL", "\"${baseUrl.escapeBuildConfigString()}\"")
        buildConfigField("String", "ADMIN_TOKEN", "\"${adminToken.escapeBuildConfigString()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

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
