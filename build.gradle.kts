plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "com.avci"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.0")
    implementation("ai.koog:koog-agents:0.6.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.avci.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
}

// Agent-specific run tasks
tasks.register<JavaExec>("runBirthdayAgent") {
    group = "application"
    description = "Run the Birthday PR Recap Agent"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("birthday")
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.register<JavaExec>("runUITestAnalyzer") {
    group = "application"
    description = "Run the UI Test Analyzer Agent"
    mainClass.set("com.avci.MainKt")
    args = listOf("uitest")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.avci.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)
}