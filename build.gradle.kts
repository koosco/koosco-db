plugins {
    kotlin("jvm") version "2.1.20"
    id("application")
}

group = "com.koosco"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.koosco.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
