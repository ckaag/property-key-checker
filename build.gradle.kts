plugins {
    java
    kotlin("jvm") version "1.3.72"
    `application`
}

group = "com.github.ckaag"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClassName = "com.github.ckaag.propertykeychecker.MainKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testCompile("junit", "junit", "4.12")
    implementation("com.github.ajalt:clikt:2.7.1")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
