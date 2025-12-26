plugins {
    kotlin("jvm") version "1.9.24"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.example.MainKt")
}

// задача run для приема аргументов
tasks.named<JavaExec>("run") {
    standardInput = System.`in`

    if (project.hasProperty("args")) {
        args = (project.findProperty("args") as String).split("\\s+".toRegex())
    }
}

// задача для запуска файлов
tasks.register<JavaExec>("runFile") {
    group = "application"
    description = "Run matrix DSL file"
    mainClass.set("org.example.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath

    if (project.hasProperty("file")) {
        args = listOf(project.property("file") as String)
    }
}

// Создание исполняемого JAR
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
