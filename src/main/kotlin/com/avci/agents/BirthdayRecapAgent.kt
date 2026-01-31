package com.avci.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import com.avci.core.llm.LLMProviderConfig
import com.avci.tools.common.CheckBirthday
import com.avci.tools.github.FetchGitHubPRs
import com.avci.tools.github.GetGitHubUsername

/**
 * AI Agent that creates personalized birthday recaps for team members.
 * 
 * Workflow:
 * 1. Check if anyone has a birthday today
 * 2. Get their GitHub username
 * 3. Fetch their recent PRs
 * 4. Generate a celebratory recap
 */
object BirthdayRecapAgent {

    private val systemPrompt = """
        You are a Birthday Celebration Assistant that creates personalized year-in-review recaps for developers.
        
        Your workflow:
        1. First, use the 'check_birthday' tool to check if anyone has a birthday today
        2. If NO ONE has a birthday today: Politely inform and wish everyone a great day
        3. If someone HAS a birthday today:
           a. Use the 'get_github_username' tool to get their GitHub username
           b. Use the 'fetch_github_prs' tool to get their recent merged PRs (ALWAYS use limit=15)
           c. Create a warm, celebratory recap for EACH birthday person
        
        When creating the recap, highlight:
        - Types of contributions (bug fixes, features, refactoring)
        - Notable achievements from recent PRs
        - Key focus areas
        - Lines of code added/removed if available
        - A motivating, celebratory closing message
        
        Be enthusiastic, warm, and personal. Use emojis sparingly but effectively.
        
        Example recap style:
        "🎂 Happy Birthday Onuralp! What an incredible year! Looking at your recent work, 
        you've been crushing it - squashing bugs and shipping amazing features. 
        Here's to another year of great commits! 🚀"
    """.trimIndent()

    fun create(llmConfig: LLMProviderConfig = LLMProviderConfig.Ollama.fromEnv()): AIAgent<String, String> {
        println("🎂 [AGENT] Creating BirthdayRecapAgent")
        println("   └─ LLM: ${llmConfig::class.simpleName} - ${llmConfig.model}")
        
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(CheckBirthday)
            tool(GetGitHubUsername)
            tool(FetchGitHubPRs)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "birthday-recap-agent",
                params = LLMParams(temperature = llmConfig.temperature)
            ) {
                system(content = systemPrompt)
            },
            model = llmConfig.createModel(),
            maxAgentIterations = 20
        )

        return AIAgent(
            promptExecutor = llmConfig.createExecutor(),
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
}

