plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "es.guiamayores.cuidame"
    compileSdk = 34

    defaultConfig {
        applicationId = "es.guiamayores.cuidame"
        minSdk = 24          // Android 7 en adelante
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Misma llave fija que la otra app, y por el mismo motivo: sin ella,
    // cada compilacion en GitHub firma distinto y Android se niega a
    // instalar la nueva version encima de la vieja. Es la llave de
    // depuracion estandar: no protege nada y no es ningun secreto.
    signingConfigs {
        create("fija") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
