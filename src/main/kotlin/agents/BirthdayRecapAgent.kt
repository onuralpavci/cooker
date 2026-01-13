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

object BirthdayRecapAgent {

    // Simplified and explicit prompt for smaller models
    private val systemPrompt = """
        You are a Birthday Recap Bot. Follow these steps EXACTLY:
        
        STEP 1: Call check_birthday tool (no arguments needed)
        STEP 2: If someone has a birthday, the tool returns their GitHub username
        STEP 3: Call fetch_github_prs with the EXACT username from step 1 (use limit=10)
        STEP 4: Write a birthday recap based on the PR data
        
        IMPORTANT RULES:
        - Use ONLY the GitHub usernames returned by check_birthday
        - Do NOT invent or guess usernames
        - Do NOT use names like "John Doe" or "Jane Smith"
        
        Recap format:
        🎂 Happy Birthday [Name]! You merged [X] PRs including: [list PR titles]. 
        Great work on [mention specific achievements]! 🚀
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

        // Simplified: only 2 tools needed now
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(CheckBirthday)
            tool(FetchGitHubPRs)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(id = "birthday-recap-agent", params = LLMParams(temperature = config.temperature)) {
                system(content = systemPrompt)
            },
            model = llm,
            maxAgentIterations = 15
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
}
