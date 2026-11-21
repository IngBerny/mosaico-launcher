import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clave de firma fija: así el teléfono acepta cada versión nueva encima de la anterior.
val signing = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.ingberny.mosaico"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ingberny.mosaico"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "2.3"
    }

    signingConfigs {
        create("mosaico") {
            storeFile = file(signing.getProperty("storeFile", "mosaico-release.jks"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mosaico")
        }
        debug {
            signingConfig = signingConfigs.getByName("mosaico")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
