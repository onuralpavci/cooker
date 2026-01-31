package com.avci.core.llm

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Abstract LLM Provider for different AI backends.
 * 
 * Supported providers:
 * - Ollama (local)
 * - OpenAI (remote) - TODO
 * - Anthropic (remote) - TODO
 * - Groq (remote) - TODO
 */
sealed class LLMProviderConfig {
    abstract val model: String
    abstract val temperature: Double
    abstract val contextLength: Long
    
    abstract fun createExecutor(): PromptExecutor
    abstract fun createModel(): LLModel
    
    /**
     * Ollama local LLM provider
     */
    data class Ollama(
        val baseUrl: String = "http://localhost:11434",
        override val model: String = "gpt-oss:20b",
        override val temperature: Double = 0.7,
        override val contextLength: Long = 128000L
    ) : LLMProviderConfig() {
        
        override fun createExecutor(): PromptExecutor {
            println("🔧 [LLM] Creating Ollama executor: $baseUrl")
            return simpleOllamaAIExecutor(baseUrl = baseUrl)
        }
        
        override fun createModel(): LLModel {
            println("🔧 [LLM] Creating Ollama model: $model (context: $contextLength)")
            return LLModel(
                provider = LLMProvider.Ollama,
                id = model,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Schema.JSON.Basic,
                    LLMCapability.Tools
                ),
                contextLength = contextLength
            )
        }
        
        companion object {
            fun fromEnv() = Ollama(
                baseUrl = System.getenv("OLLAMA_URL") ?: "http://localhost:11434",
                model = System.getenv("OLLAMA_MODEL") ?: "gpt-oss:20b",
                temperature = System.getenv("OLLAMA_TEMPERATURE")?.toDoubleOrNull() ?: 0.7
            )
        }
    }
    
    /**
     * OpenAI remote LLM provider
     */
    data class OpenAI(
        val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "",
        override val model: String = "gpt-4o-mini",
        override val temperature: Double = 0.7,
        override val contextLength: Long = 128000L
    ) : LLMProviderConfig() {
        
        override fun createExecutor(): PromptExecutor {
            println("🔧 [LLM] Creating OpenAI executor")
            require(apiKey.isNotBlank()) { "OPENAI_API_KEY is required" }
            return simpleOpenAIExecutor(apiKey)
        }
        
        override fun createModel(): LLModel {
            println("🔧 [LLM] Creating OpenAI model: $model")
            return modelFromString(model)
        }

        /**
         * Map string model names to OpenAI model objects
         *
         * Supported models:
         * - gpt-4o-mini: Cheap & fast (default) - $0.15/1M input
         * - GPT-4o: Powerful & balanced - $2.50/1M input
         * - GPT-4.1-nano: Ultra cheap, 1M context - $0.10/1M input
         * - o3-mini: Reasoning model, affordable - $1.10/1M input
         */
        private fun modelFromString(modelName: String): LLModel = when (modelName.lowercase()) {
            "gpt-4o-mini", "4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4o", "4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4.1-nano", "4.1-nano", "nano" -> OpenAIModels.Chat.GPT4_1Nano
            "o3-mini" -> OpenAIModels.Chat.O3Mini
            else -> {
                println("⚠️ Unknown model '$modelName', using gpt-4o-mini")
                OpenAIModels.Chat.GPT4oMini
            }
        }

        companion object {
            
            fun fromEnv() = OpenAI(
                apiKey = System.getenv("OPENAI_API_KEY") ?: "",
                model = System.getenv("OPENAI_MODEL") ?: "gpt-4o-mini",
                temperature = System.getenv("OPENAI_TEMPERATURE")?.toDoubleOrNull() ?: 0.7
            )
        }
    }
    
    /**
     * Anthropic (Claude) remote LLM provider
     * TODO: Implement when needed
     */
    data class Anthropic(
        val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
        override val model: String = "claude-3-5-sonnet-20241022",
        override val temperature: Double = 0.7,
        override val contextLength: Long = 200000L
    ) : LLMProviderConfig() {
        
        override fun createExecutor(): PromptExecutor {
            // TODO: Implement Anthropic executor
            println("🔧 [LLM] Anthropic provider not yet implemented, falling back to Ollama")
            return simpleOllamaAIExecutor(baseUrl = "http://localhost:11434")
        }
        
        override fun createModel(): LLModel {
            // TODO: Implement Anthropic model
            return LLModel(
                provider = LLMProvider.Anthropic,
                id = model,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Schema.JSON.Basic,
                    LLMCapability.Tools
                ),
                contextLength = contextLength
            )
        }
        
        companion object {
            fun fromEnv() = Anthropic(
                apiKey = System.getenv("ANTHROPIC_API_KEY") ?: "",
                model = System.getenv("ANTHROPIC_MODEL") ?: "claude-3-5-sonnet-20241022",
                temperature = System.getenv("ANTHROPIC_TEMPERATURE")?.toDoubleOrNull() ?: 0.7
            )
        }
    }
}

