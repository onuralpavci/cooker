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
    
    // Logging
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.0")
    
    // AI Agents Framework
    implementation("ai.koog:koog-agents:0.6.0")
    
    // OpenAI Client
    implementation("ai.koog:prompt-executor-openai-client:0.6.0")
    
    // Date/Time utilities
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    
    // JSON serialization (already included in koog-agents, but explicit for tools)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.avci.MainKt")
}

// Pass all command line args to the application
tasks.withType<JavaExec> {
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
}

// Agent Tasks
tasks.register<JavaExec>("runBirthdayAgent") {
    group = "agents"
    description = "Run the Birthday PR Recap Agent"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("birthday")
}

tasks.register<JavaExec>("runUITestAnalyzer") {
    group = "agents"
    description = "Run the UI Test Analyzer Agent"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("uitest")
}

tasks.register<JavaExec>("runSlackSummarizer") {
    group = "agents"
    description = "Run the Slack Summarizer Agent"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("slack")
}

// Tool Testing Tasks
tasks.register<JavaExec>("testSlackTools") {
    group = "tools"
    description = "Test Slack tools - list channels"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("tool:list_slack_channels")
}

tasks.register<JavaExec>("showConfig") {
    group = "tools"
    description = "Show current Cooker configuration"
    mainClass.set("com.avci.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("config")
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