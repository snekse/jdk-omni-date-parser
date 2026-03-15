plugins {
    java
    `java-library`
    id("me.champeau.jmh") version "0.7.2"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "io.github.snekse"
version = findProperty("releaseVersion")?.toString() ?: "dev-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("jdk-omni-date-parser")
        description.set("A lenient universal date parser for JVM languages")
        url.set("https://github.com/snekse/jdk-omni-date-parser")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("snekse")
                name.set("Derek Eskens")
                url.set("https://github.com/snekse")
            }
        }
        scm {
            url.set("https://github.com/snekse/jdk-omni-date-parser")
            connection.set("scm:git:git://github.com/snekse/jdk-omni-date-parser.git")
            developerConnection.set("scm:git:ssh://github.com:snekse/jdk-omni-date-parser.git")
        }
    }
}
