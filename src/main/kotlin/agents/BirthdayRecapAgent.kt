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
           b. Use the 'fetch_github_prs' tool to get their recent merged Pull Requests (ALWAYS use limit=10)
           c. Create a warm, celebratory recap paragraph for EACH birthday person
        
        When creating the recap, highlight:
        - Total number of PRs they've merged
        - Types of contributions (bug fixes, new features, refactoring, documentation)
        - Notable achievements or interesting PR titles
        - Lines of code added/removed if available
        - A motivating, celebratory closing message
        
        Be enthusiastic, warm, and personal in your recap. Use emojis sparingly but effectively.
        Write the recap in a conversational, friendly tone as if you're a colleague celebrating with them.
        
        Example recap style:
        "🎂 Happy Birthday Onuralp! What an incredible year you've had! You merged X PRs, squashed bugs like a pro, 
        and shipped amazing features like [feature name]. Your dedication to clean code shows - 
        you even took time for refactoring! Here's to another year of great commits! 🚀"
        
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
            prompt = prompt(id = "birthday-recap-agent", params = LLMParams(temperature = config.temperature)) {
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
