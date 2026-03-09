plugins {
    java
    `java-library`
    id("me.champeau.jmh") version "0.7.2"
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

    jmh(libs.jmhCore)
    jmhAnnotationProcessor(libs.jmhGeneratorAnnotationProcessor)
}

tasks.test {
    useJUnitPlatform()
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    resultFormat = "TEXT"
    benchmarkMode = listOf("thrpt")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "io.github.snekse"
            artifactId = "jdk-omni-date-parser"
            version = project.version.toString()

            pom {
                name = "jdk-omni-date-parser"
                description = "A lenient universal date parser for JVM languages"
                url = "https://github.com/snekse/jdk-omni-date-parser"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer { id = "snekse" }
                }
                scm {
                    connection = "scm:git:git://github.com/snekse/jdk-omni-date-parser.git"
                    url = "https://github.com/snekse/jdk-omni-date-parser"
                }
            }
        }
    }
    repositories {
        // TODO: configure Sonatype OSSRH when publishing
    }
}

// Signing — required for Maven Central
// signing { sign(publishing.publications["mavenJava"]) }
// TODO: uncomment and configure signing key when publishing
