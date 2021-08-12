plugins {
    java
    kotlin("jvm") version "1.5.21"
    id("com.diffplug.spotless") version "5.9.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
}

group = "com.symbaloo"
version = "1.0.0"
description = "graphql-java instrumentation for micrometer metrics"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.graphql-java:graphql-java:16.2")
    implementation("io.micrometer:micrometer-core:1.5.11")

    testImplementation("org.slf4j:slf4j-simple:1.7.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
