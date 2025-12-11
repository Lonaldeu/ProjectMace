plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.1"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

group = "me.lonaldeu"
version = "0.1.2"
val artifactName = "ProjectMace"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/releases/")
    }
}

dependencies {
    // Server-provided APIs (NOT shaded) - Paper already includes Adventure
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    
    // Optional PlaceholderAPI integration (priority over MiniPlaceholders)
    compileOnly("me.clip:placeholderapi:2.11.6")
    
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
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set(artifactName)
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn("proguard")
}

task("proguard", proguard.gradle.ProGuardTask::class) {
    dependsOn(tasks.shadowJar)
    configuration("proguard.pro")
    
    val shadowJar = tasks.shadowJar.get()
    
    injars(shadowJar.archiveFile)
    outjars(shadowJar.archiveFile.get().asFile.parentFile.resolve("${shadowJar.archiveBaseName.get()}-${shadowJar.archiveVersion.get()}-obf.jar"))
    
    // Automatically find library jars
    libraryjars(sourceSets.main.get().compileClasspath)
    
    // Get JDK 21 path from toolchain service
    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
    val javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    val jdkHome = javaLauncher.get().metadata.installationPath.asFile
    libraryjars(jdkHome.resolve("jmods"))
}

tasks.test {
    enabled = false
}
