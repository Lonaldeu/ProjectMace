plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.1"
}

group = "com.tondomace"
version = "0.1.0"
val artifactName = "TondoMace"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Server-provided APIs (NOT shaded) - Paper already includes Adventure
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    
    // fastutil-core keeps just the object/int/long/double collections we use
    implementation("it.unimi.dsi:fastutil-core:8.5.16")

    implementation("com.google.code.gson:gson:2.11.0")
    
    // Optional MiniPlaceholders integration (compileOnly to keep it soft)
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.0.1")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    named("main") {
        java.setSrcDirs(emptyList<String>())
    }
    named("test") {
        java.setSrcDirs(emptyList<String>())
    }
}

tasks.shadowJar {
    // Relocate dependencies to avoid conflicts
    relocate("it.unimi.dsi.fastutil", "com.tondomace.libs.fastutil")
    
    archiveClassifier.set("")
    archiveBaseName.set(artifactName)
}

val deployShadowJar = tasks.register<Copy>("deployShadowJar") {
    val shadowJarTask = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
    dependsOn(shadowJarTask)
    from(shadowJarTask.flatMap { it.archiveFile })
    into(provider { file("/Users/ronald/Desktop/Folia SMP/plugins") }) 
    doLast {
        logger.lifecycle("Copied shadow jar to \"${destinationDir.absolutePath}\"")
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    finalizedBy(deployShadowJar)
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    enabled = false
}
