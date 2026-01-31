package com.avci.core.config

import com.avci.core.llm.LLMProviderConfig

/**
 * Centralized configuration for Cooker AI Agents.
 * 
 * All configuration can be loaded from environment variables or provided directly.
 */
data class CookerConfig(
    // LLM Configuration
    val llmProvider: LLMProviderConfig = LLMProviderConfig.Ollama.fromEnv(),
    
    // GitHub Configuration
    val github: GitHubConfig = GitHubConfig.fromEnv(),
    
    // Slack Configuration
    val slack: SlackConfig = SlackConfig.fromEnv(),
    
    // Jira Configuration
    val jira: JiraConfig = JiraConfig.fromEnv(),
    
    // Figma Configuration
    val figma: FigmaConfig = FigmaConfig.fromEnv(),
    
    // Google Play Configuration
    val playStore: PlayStoreConfig = PlayStoreConfig.fromEnv()
) {
    companion object {
        fun fromEnv(): CookerConfig {
            println("📋 [CONFIG] Loading configuration from environment...")
            return CookerConfig()
        }
        
        fun forTesting(): CookerConfig {
            println("📋 [CONFIG] Creating test configuration...")
            return CookerConfig(
                llmProvider = LLMProviderConfig.Ollama(
                    baseUrl = "http://localhost:11434",
                    model = "llama3.2"
                )
            )
        }
    }
    
    fun printSummary() {
        println("""
            |
            |📋 Cooker Configuration
            |${"=".repeat(50)}
            |LLM Provider: ${llmProvider::class.simpleName}
            |  - Model: ${llmProvider.model}
            |  - Temperature: ${llmProvider.temperature}
            |
            |GitHub: ${if (github.isConfigured()) "✅ Configured" else "❌ Not configured"}
            |Slack: ${if (slack.isConfigured()) "✅ Configured" else "❌ Not configured"}
            |Jira: ${if (jira.isConfigured()) "✅ Configured" else "❌ Not configured"}
            |Figma: ${if (figma.isConfigured()) "✅ Configured" else "❌ Not configured"}
            |Play Store: ${if (playStore.isConfigured()) "✅ Configured" else "❌ Not configured"}
            |${"=".repeat(50)}
        """.trimMargin())
    }
}

/**
 * GitHub-specific configuration
 */
data class GitHubConfig(
    val token: String = "",
    val defaultOrg: String = "midas-engineering",
    val defaultRepo: String = "mobile-android"
) {
    fun isConfigured() = token.isNotBlank() || isGhCliAuthenticated()
    
    private fun isGhCliAuthenticated(): Boolean {
        return try {
            val process = ProcessBuilder("gh", "auth", "status")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    companion object {
        fun fromEnv() = GitHubConfig(
            token = System.getenv("GITHUB_TOKEN") ?: "",
            defaultOrg = System.getenv("GITHUB_ORG") ?: "midas-engineering",
            defaultRepo = System.getenv("GITHUB_REPO") ?: "mobile-android"
        )
    }
}

/**
 * Slack-specific configuration
 */
data class SlackConfig(
    val botToken: String = "",
    val webhookUrl: String = "",
    val defaultChannel: String = ""
) {
    fun isConfigured() = botToken.isNotBlank() || webhookUrl.isNotBlank()
    
    companion object {
        fun fromEnv() = SlackConfig(
            botToken = System.getenv("SLACK_BOT_TOKEN") ?: "",
            webhookUrl = System.getenv("SLACK_UI_TEST_WEBHOOK_URL") ?: "",
            defaultChannel = System.getenv("SLACK_DEFAULT_CHANNEL") ?: ""
        )
    }
}

/**
 * Jira-specific configuration
 */
data class JiraConfig(
    val baseUrl: String = "",
    val email: String = "",
    val apiToken: String = "",
    val defaultProject: String = ""
) {
    fun isConfigured() = baseUrl.isNotBlank() && apiToken.isNotBlank()
    
    companion object {
        fun fromEnv() = JiraConfig(
            baseUrl = System.getenv("JIRA_BASE_URL") ?: "",
            email = System.getenv("JIRA_EMAIL") ?: "",
            apiToken = System.getenv("JIRA_API_TOKEN") ?: "",
            defaultProject = System.getenv("JIRA_DEFAULT_PROJECT") ?: ""
        )
    }
}

/**
 * Figma-specific configuration
 */
data class FigmaConfig(
    val accessToken: String = "",
    val teamId: String = ""
) {
    fun isConfigured() = accessToken.isNotBlank()
    
    companion object {
        fun fromEnv() = FigmaConfig(
            accessToken = System.getenv("FIGMA_ACCESS_TOKEN") ?: "",
            teamId = System.getenv("FIGMA_TEAM_ID") ?: ""
        )
    }
}

/**
 * Google Play Store configuration
 */
data class PlayStoreConfig(
    val packageName: String = "",
    val serviceAccountJson: String = ""
) {
    fun isConfigured() = packageName.isNotBlank() && serviceAccountJson.isNotBlank()
    
    companion object {
        fun fromEnv() = PlayStoreConfig(
            packageName = System.getenv("PLAY_STORE_PACKAGE_NAME") ?: "",
            serviceAccountJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") ?: ""
        )
    }
}

