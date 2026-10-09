// Pure Kotlin/JVM module: all product rules that do not need the Android framework.
// It deliberately avoids the kotlinx.serialization compiler plugin (JSON is handled
// through the JsonElement tree API) so it can also be compiled and tested offline
// with tools/verify-domain.sh.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
