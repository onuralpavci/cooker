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
import com.avci.tools.BirthdayRecapTool

object BirthdayRecapAgent {

    // Super simple prompt - just one tool to call
    private val systemPrompt = """
        You are a Birthday Recap Bot.
        
        STEP 1: Call the get_birthday_recap_data tool (no arguments needed)
        STEP 2: Read the PR data returned by the tool
        STEP 3: Write a warm, celebratory birthday message for each person
        
        Your recap should mention:
        - Their name
        - Number of PRs they merged
        - Some PR titles (bugs fixed, features added)
        - A motivating closing message
        
        Use emojis like 🎂 🚀 ✨
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

        // Only ONE tool needed now!
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(BirthdayRecapTool)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(id = "birthday-recap-agent", params = LLMParams(temperature = config.temperature)) {
                system(content = systemPrompt)
            },
            model = llm,
            maxAgentIterations = 5  // Only need a few iterations now
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
}
