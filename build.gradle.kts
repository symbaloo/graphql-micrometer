plugins {
    java
    kotlin("jvm") version "1.5.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.graphql-java:graphql-java:16.2")
    implementation("io.micrometer:micrometer-core:1.5.11")
}