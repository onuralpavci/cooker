package com.avci.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.avci.OllamaConfig
import com.avci.tools.CheckBirthday
import com.avci.tools.FetchGitHubPRs
import com.avci.tools.GetGitHubUsername

object BirthdayRecapAgent {

    private val systemPrompt = """
        You are a Birthday Celebration Assistant that creates personalized year-in-review recaps for developers.
        
        Your workflow:
        1. First, use the 'check_birthday' tool to check if anyone has a birthday today (no arguments needed)
        2. If NO ONE has a birthday today: Politely inform and wish everyone a great day
        3. If someone HAS a birthday today:
           a. Use the 'get_github_username' tool to get their GitHub username (pass their name)
           b. Use the 'fetch_github_prs' tool to get their recent merged Pull Requests (ALWAYS use limit=15)
           c. Create a warm, celebratory recap paragraph for EACH birthday person
        
        When creating the recap, focus on their RECENT work and highlight:
        - Types of contributions they've been working on (bug fixes, new features, refactoring, documentation)
        - Notable achievements or interesting PR titles from their recent work
        - Key areas they've been focusing on lately
        - Lines of code added/removed if available
        - A motivating, celebratory closing message
        
        IMPORTANT: Don't mention the exact number of PRs as "total" - you're only seeing their last 15 PRs.
        Instead, focus on what they've been working on recently and celebrate their recent contributions.
        
        Be enthusiastic, warm, and personal in your recap. Use emojis sparingly but effectively.
        Write the recap in a conversational, friendly tone as if you're a colleague celebrating with them.
        
        Example recap style:
        "🎂 Happy Birthday Onuralp! What an incredible year you've had! Looking at your recent work, you've been crushing it - 
        squashing bugs like a pro and shipping amazing features like [feature name]. Your dedication to clean code really shows, 
        and it's clear you've been focusing on [area]. Here's to another year of great commits! 🚀"
        
        If there are multiple birthday people, create a recap for each one.
    """.trimIndent()

    fun create(config: OllamaConfig = OllamaConfig.default()): AIAgent<String, String> {
        val llm = LLModel(
            provider = LLMProvider.Ollama,
            id = config.model,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 128000,
        )

        val executor = simpleOllamaAIExecutor(
            baseUrl = config.baseUrl,
        )

        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(CheckBirthday)
            tool(GetGitHubUsername)
            tool(FetchGitHubPRs)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "birthday-recap-agent",
                params = LLMParams(temperature = config.temperature)
            ) {
                system(content = systemPrompt)
            },
            model = llm,
            maxAgentIterations = 20 // More iterations for multiple birthday people
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
}
