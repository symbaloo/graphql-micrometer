plugins {
    kotlin("jvm") version "1.5.21"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
    id("org.jetbrains.dokka") version "1.5.0"
    `java-library`
    `maven-publish`
    signing
}

group = "com.symbaloo"
version = "1.0.1"
description = "graphql-java instrumentation for micrometer metrics"

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")
val repoName = name
val repoDescription = description
val repoUrl = "https://github.com/symbaloo/graphql-micrometer"

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

    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    javadoc {
        dependsOn("dokkaJavadoc")
    }

    register<Jar>("javadocJar") {
        dependsOn("javadoc")
        archiveClassifier.set("javadoc")
        from("$buildDir/javadoc")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set(repoUrl)

                licenses {
                    license {
                        name.set("MIT")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("arian")
                        name.set("Arian Stolwijk")
                        email.set("arian@symbaloo.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/symbaloo/graphql-micrometer.git")
                    url.set(repoUrl)
                }
            }
        }

        repositories {
            maven {

                val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
                url = if (isReleaseVersion) releasesRepoUrl else snapshotsRepoUrl

                // these can be set through gradle.properties
                if (properties.containsKey("mavenRepoUser")) {
                    credentials {
                        username = properties["sonatypeRepoUser"] as String?
                        password = properties["sonatypeRepoPassword"] as String?
                    }
                }
            }
        }
    }
}

signing {
    setRequired { isReleaseVersion && gradle.taskGraph.hasTask("publish") }
    if (isReleaseVersion) {
        sign(publishing.publications)
    }
}
