plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.punyo.kotlinmcpserver.MainKt")
}

group = "com.punyo.kotlinmcpserver"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(libs.mcp.sdk)
    implementation(libs.slf4j.nop)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}