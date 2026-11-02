import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("signing/keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val computedVersionCode = ciRunNumber
val computedVersionName = "1.0.$ciRunNumber"

android {
    namespace = "edu.garvard.justice.course"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.garvard.justice.course"
        minSdk = 24
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO", "\"zigorminsk-debug/garvard\"")
        buildConfigField("String", "SIGNING_KEY_SHA256", "\"14:6C:D7:AF:A4:35:F8:E9:3C:05:EA:1F:92:3F:DC:F1:14:05:31:BF:9F:A8:A5:DA:68:D6:74:7F:8A:E1:94:D8\"")
    }

    signingConfigs {
        create("persistentKey") {
            storeFile = rootProject.file("signing/garvard-release-key.jks")
            storePassword = keystoreProps.getProperty("storePassword", "garvard2026")
            keyAlias = keystoreProps.getProperty("keyAlias", "garvard_justice")
            keyPassword = keystoreProps.getProperty("keyPassword", "garvard2026")
            storeType = keystoreProps.getProperty("storeType", "PKCS12")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("persistentKey")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Подписываем тем же постоянным ключом, чтобы любая сборка устанавливалась поверх без конфликта подписей
            signingConfig = signingConfigs.getByName("persistentKey")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.webkit:webkit:1.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
