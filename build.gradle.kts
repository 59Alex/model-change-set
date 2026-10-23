plugins {
    `java-library`
    `maven-publish`
}

val testJavaVersion = providers.gradleProperty("testJavaVersion").map(String::toInt)
    .getOrElse(JavaVersion.current().majorVersion.toInt())

group = "io.github.59alex"
version = "0.3.0"

repositories { mavenCentral() }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.withType<Jar>().configureEach {
    into("META-INF") { from("LICENSE") }
}

val examples = sourceSets.create("examples")
configurations[examples.implementationConfigurationName].extendsFrom(configurations.implementation.get())
examples.compileClasspath += sourceSets.main.get().output
examples.runtimeClasspath += sourceSets.main.get().output

tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Compare two schema snapshots and print their change set."
    classpath = examples.runtimeClasspath
    mainClass.set("io.github.alex59.modelchanges.examples.SchemaDiff")
}

tasks.check { dependsOn(tasks.named(examples.classesTaskName)) }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("model-change-set")
                description.set("Deterministic, field-aware change sets for keyed Java models")
                url.set("https://github.com/59Alex/model-change-set")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer { id.set("59Alex"); name.set("Aleksandr Kashevarov") }
                }
                scm {
                    url.set("https://github.com/59Alex/model-change-set")
                    connection.set("scm:git:https://github.com/59Alex/model-change-set.git")
                }
            }
        }
    }
}

// Examples use the selected LTS runtime, while all sources target Java 17.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    })
}
