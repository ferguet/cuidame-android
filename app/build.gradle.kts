plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "es.guiamayores.cuidame"
    compileSdk = 34

    defaultConfig {
        applicationId = "es.guiamayores.cuidame"
        // SUBE DE ANDROID 7 A ANDROID 8.
        //
        // Lo pide Health Connect, que es el almacen de salud de Android y
        // la unica via limpia de leer lo que mide una pulsera. No me gusta
        // dejar fuera moviles, pero Android 7 es de 2016 y ya casi no
        // queda ninguno en uso; el propio Health Connect ni siquiera
        // funciona por debajo de Android 9.
        minSdk = 26          // Android 8 en adelante
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

    // Leer lo que la pulsera deja en el almacen de salud de Android.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    // Health Connect trabaja con funciones "suspend": hacen falta corrutinas.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
