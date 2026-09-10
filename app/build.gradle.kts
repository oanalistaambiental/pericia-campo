plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.oanalistaambiental.pericia"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.oanalistaambiental.pericia"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Mapa de referencia — OpenStreetMap via osmdroid, de uso publico e sem chave de API (ao
    // contrario do Google Maps, que exige conta Google Cloud com faturamento habilitado).
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // OCR local do parecer fotografado (Condicionantes e prazos) — modelo embarcado no APK,
    // funciona sem rede. Variante "bundled" (nao a "unbundled" via Play Services), de proposito:
    // nao depende de baixar modelo na hora, mesmo espirito offline-first do resto do app.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
// org.json faz parte do Android, mas nos testes de unidade e apenas um esqueleto;
// esta dependencia da a implementacao real para a JVM.
testImplementation("org.json:json:20240303")
}
