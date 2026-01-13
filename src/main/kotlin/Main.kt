package com.avci

import com.avci.agents.BirthdayRecapAgent
import kotlinx.coroutines.runBlocking

fun main() {
    println("🎂 Birthday PR Recap Agent")
    println("=".repeat(50))
    runBlocking {
        runBirthdayRecapAgent()
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
