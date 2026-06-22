plugins {
    kotlin("jvm") version "1.9.22"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    // Android SDK stub classes (specifically API 33+)
    compileOnly("com.google.android:android:4.1.1.4")
    compileOnly("androidx.appcompat:appcompat:1.6.1")
}

tasks.jar {
    archiveFileName.set("compiled-classes.jar")
}
