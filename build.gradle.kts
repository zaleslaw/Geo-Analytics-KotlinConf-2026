plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "com.zaleslaw"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://repo.osgeo.org/repository/release")
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:dataframe:1.0.0-Beta5")
    implementation("org.jetbrains.kotlinx:dataframe-geo:1.0.0-Beta5")
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.8.4")
    implementation("org.jetbrains.kotlinx:kandy-geo:0.8.4")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("org.geotools:gt-referencing:30.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Weather API
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib-jdk8"))
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.zaleslaw.MainKt")
}

tasks.test {
    useJUnitPlatform()
}