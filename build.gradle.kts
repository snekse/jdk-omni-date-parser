plugins {
    java
    `java-library`
    // TODO Phase 3: id("io.github.reyerizo.jmh") version "0.8.2"
    `maven-publish`
    signing
}

group = "io.github.snekse"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}

// TODO: group ID, description, SCM, developer info for Maven Central
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "jdk-omni-date-parser"
                description = "Lenient JDK-based date parser that converts almost any date/time string to java.time results"
                // TODO: fill in url, licenses, developers, scm before first release
            }
        }
    }
}
