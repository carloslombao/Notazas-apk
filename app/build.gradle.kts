plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.notazas.mobil"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.notazas.mobil"
        minSdk = 26
        targetSdk = 36
        // El CI (GitHub Actions) inyecta versiones crecientes con -Pci.versionCode / -Pci.versionName:
        // Android solo acepta una actualización con versionCode mayor, y AutoUpdater compara versionName.
        // En local (sin esas propiedades) queda 1 / "1.0".
        versionCode = (project.findProperty("ci.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("ci.versionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Firma de release con el debug.keystore global del usuario (~/.android/debug.keystore,
    // contraseñas estándar). Para poder ACTUALIZAR una instalación existente, el APK debe estar firmado
    // con la MISMA clave: en CI se restaura desde el secreto DEBUG_KEYSTORE_BASE64 (ver build-apk.yml).
    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navegación
    implementation(libs.androidx.navigation.compose)

    // Almacenamento local (usuario por defecto, IP do servidor, etc.)
    implementation(libs.androidx.datastore.preferences)

    // Acceso con biometría
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    // Rede: fala coa mesma API FastAPI que xa usa a web
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Fotos (avatares, fotos de traballos)
    implementation(libs.coil.compose)

    // Escaneo do QR de aprovisionamento (cámara + lectura de códigos)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Almacenamento cifrado dos tokens de BoxAbalar/aprovisionamento remoto
    implementation(libs.androidx.security.crypto)

    // Actualización automática por APK (JitPack). 1.0.1 = etiqueta estable da librería (a mesma que usa
    // outro proxecto público, sameerasw/essentials). Se JitPack non a resolve, proba "master-SNAPSHOT".
    implementation("com.github.CSAbhiOnline:AutoUpdater:1.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
