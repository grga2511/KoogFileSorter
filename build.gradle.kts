plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:1.3.0")
    implementation("ai.koog:koog-agents-additions:1.3.0-beta")
    implementation("ai.koog:http-client-ktor:1.3.0")


    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.3")
    testImplementation("io.kotest:kotest-assertions-core:6.0.3")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}