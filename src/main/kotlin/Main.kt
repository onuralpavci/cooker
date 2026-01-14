package com.avci

import com.avci.agents.BirthdayRecapAgent
import com.avci.agents.UITestAnalyzerAgent
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val agentType = args.firstOrNull() ?: System.getenv("AGENT_TYPE") ?: "birthday"
    
    runBlocking {
        when (agentType.lowercase()) {
            "birthday" -> {
                println("🎂 Birthday PR Recap Agent")
                println("=".repeat(50))
                runBirthdayRecapAgent()
            }
            "uitest" -> {
                println("🧪 UI Test Analyzer Agent")
                println("=".repeat(50))
                runUITestAnalyzerAgent()
            }
            else -> {
                println("❌ Unknown agent type: $agentType")
                println("Available agents: birthday, uitest")
                println("\nUsage:")
                println("  ./gradlew runBirthdayAgent    - Run Birthday PR Recap Agent")
                println("  ./gradlew runUITestAnalyzer   - Run UI Test Analyzer Agent")
            }
        }
    }
}

data class OllamaConfig(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "gpt-oss:20b",
    val temperature: Double = 0.7
) {
    companion object {
        fun default() = OllamaConfig()

        fun fromEnv() = OllamaConfig(
            baseUrl = System.getenv("OLLAMA_URL") ?: "http://localhost:11434",
            model = System.getenv("OLLAMA_MODEL") ?: "gpt-oss:20b",
            temperature = System.getenv("OLLAMA_TEMPERATURE")?.toDoubleOrNull() ?: 0.7
        )
    }
}

suspend fun runBirthdayRecapAgent() {
    val config = OllamaConfig.fromEnv()
    println("📡 Using Ollama: ${config.baseUrl} | Model: ${config.model}")
    
    val agent = BirthdayRecapAgent.create(config)

    println("""
        |
        |This agent will:
        |1. Check if anyone has a birthday today
        |2. If yes, get their GitHub username
        |3. Fetch their recent GitHub PRs
        |4. Create a personalized year-in-review recap!
        |
    """.trimMargin())

    println("🔄 Checking for birthdays today...\n")

    val result = agent.run("Check if anyone has a birthday today and create a recap for them.")
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}

suspend fun runUITestAnalyzerAgent() {
    val config = OllamaConfig.fromEnv()
    println("📡 Using Ollama: ${config.baseUrl} | Model: ${config.model}")
    
    val agent = UITestAnalyzerAgent.create(config)

    println("""
        |
        |This agent will:
        |1. Fetch the last 10 Maestro UI Test workflow runs
        |2. Download and analyze test summaries
        |3. Identify failure patterns (NEW, BUG, FLAKY)
        |4. Generate a comprehensive report
        |
    """.trimMargin())

    println("🔄 Analyzing UI test failures...\n")

    val result = agent.run("Analyze the recent UI test failures and categorize them.")
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}
