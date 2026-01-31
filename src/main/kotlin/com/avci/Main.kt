package com.avci

import com.avci.agents.BirthdayRecapAgent
import com.avci.agents.SlackQAAgent
import com.avci.agents.SlackSummarizerAgent
import com.avci.agents.UITestAnalyzerAgent
import com.avci.core.config.CookerConfig
import com.avci.core.llm.LLMProviderConfig
import com.avci.tools.common.CheckBirthday
import com.avci.tools.common.GetTodaysDate
import com.avci.tools.figma.GetFigmaDesign
import com.avci.tools.github.AnalyzeUITestFailures
import com.avci.tools.github.FetchGitHubPRs
import com.avci.tools.jira.FetchJiraTask
import com.avci.tools.jira.SearchJiraTasks
import com.avci.tools.playstore.FetchPlayStoreReviews
import com.avci.tools.slack.FetchSlackMessages
import com.avci.tools.slack.GetChannelIdByName
import com.avci.tools.slack.GetChannelInfo
import com.avci.tools.slack.ListSlackChannels
import com.avci.tools.slack.PostSlackMessage
import kotlinx.coroutines.runBlocking

/**
 * Cooker - AI Agent Framework for Mobile Development Teams
 * 
 * Usage:
 *   ./gradlew run --args="<command>"
 * 
 * Commands:
 *   Agents:
 *     birthday    - Run Birthday PR Recap Agent
 *     uitest      - Run UI Test Analyzer Agent
 *     slack       - Run Slack Summarizer Agent
 * 
 *   Tools (for testing):
 *     tool:list_slack_channels
 *     tool:fetch_slack_messages <channelId>
 *     tool:fetch_jira_task <taskKey>
 *     tool:search_jira <jql>
 *     tool:fetch_github_prs <username>
 *     tool:analyze_ui_tests
 *     tool:get_figma_design <url>
 *     tool:fetch_playstore_reviews
 * 
 *   Utility:
 *     config      - Show current configuration
 *     help        - Show this help message
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: System.getenv("AGENT_TYPE") ?: "help"
    
    println("""
        |
        |🍳 Cooker - AI Agent Framework
        |${"=".repeat(50)}
    """.trimMargin())
    
    runBlocking {
        when {
            // Agents
            command.equals("slack-summarize", ignoreCase = true) || command.equals("slack", ignoreCase = true) -> {
                val channelId = args.getOrNull(1)
                val messageCount = args.getOrNull(2)?.toIntOrNull() ?: 50
                runSlackSummarizerAgent(channelId, messageCount)
            }
            command.equals("slack-ask", ignoreCase = true) -> {
                val channelId = args.getOrNull(1)
                val question = args.drop(2).joinToString(" ")
                runSlackAskAgent(channelId, question)
            }
            command.equals("birthday", ignoreCase = true) -> runBirthdayAgent()
            command.equals("uitest", ignoreCase = true) -> runUITestAnalyzerAgent()
            
            // Tool testing
            command.startsWith("tool:") -> runToolTest(command, args)
            
            // Utility
            command.equals("config", ignoreCase = true) -> showConfig()
            
            else -> showHelp(command)
        }
    }
}

// ============================================================================
// Agent Runners
// ============================================================================

suspend fun runBirthdayAgent() {
    println("🎂 Birthday PR Recap Agent")
    println("=".repeat(50))
    
    val config = LLMProviderConfig.Ollama.fromEnv()
    println("📡 LLM: ${config.model} @ ${config.baseUrl}")
    
    val agent = BirthdayRecapAgent.create(config)

    println("""
        |
        |This agent will:
        |1. Check if anyone has a birthday today
        |2. Get their GitHub username
        |3. Fetch their recent GitHub PRs
        |4. Create a personalized year-in-review recap!
        |
    """.trimMargin())

    val result = agent.run("Check if anyone has a birthday today and create a recap for them.")
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}

suspend fun runUITestAnalyzerAgent() {
    println("🧪 UI Test Analyzer Agent")
    println("=".repeat(50))
    
    val config = LLMProviderConfig.Ollama.fromEnv()
    val targetRepo = System.getenv("TARGET_REPO") ?: "midas-engineering/mobile-android"
    val targetWorkflow = System.getenv("TARGET_WORKFLOW") ?: "Maestro UI Test"
    val runCount = System.getenv("RUN_COUNT")?.toIntOrNull() ?: 10
    
    println("📡 LLM: ${config.model} @ ${config.baseUrl}")
    println("📊 Target: $targetRepo | Workflow: $targetWorkflow | Last $runCount runs")
    
    val agent = UITestAnalyzerAgent.create(config)

    val userInput = """
        Analyze the UI test failures for:
        - Repository: $targetRepo
        - Workflow: $targetWorkflow
        - Number of runs to analyze: $runCount
        
        Use the analyze_ui_test_failures tool with these exact parameters.
    """.trimIndent()
    
    val result = agent.run(userInput)
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}

suspend fun runSlackSummarizerAgent(channelId: String?, messageCount: Int = 50) {
    println("💬 Slack Summarizer Agent")
    println("=".repeat(50))
    
    // Check for required env
    val botToken = System.getenv("SLACK_BOT_TOKEN")
    if (botToken.isNullOrBlank()) {
        println("❌ SLACK_BOT_TOKEN environment variable is required!")
        println("   Export it: export SLACK_BOT_TOKEN='xoxb-...'")
        return
    }
    
    // Auto-detect LLM provider: OpenAI if API key set, otherwise Ollama
    val openaiKey = System.getenv("OPENAI_API_KEY")
    val config = if (!openaiKey.isNullOrBlank()) {
        val openaiConfig = LLMProviderConfig.OpenAI.fromEnv()
        println("📡 LLM: OpenAI ${openaiConfig.model}")
        openaiConfig
    } else {
        val ollamaConfig = LLMProviderConfig.Ollama.fromEnv()
        println("📡 LLM: Ollama ${ollamaConfig.model} @ ${ollamaConfig.baseUrl}")
        ollamaConfig
    }
    
    val agent = SlackSummarizerAgent.create(config)
    
    val userInput = if (channelId != null) {
        SlackSummarizerAgent.createSummarizePrompt(channelId, messageCount)
    } else {
        "Mevcut Slack kanallarını listele ve hangisini özetlememi istediğini sor"
    }
    
    println("📋 Task: ${if (channelId != null) "Summarize #$channelId ($messageCount messages)" else "Interactive mode"}")
    println()
    
    val result = agent.run(userInput)
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}

suspend fun runSlackAskAgent(channelId: String?, question: String?) {
    println("❓ Slack Q&A Agent")
    println("=".repeat(50))
    
    // Validate inputs
    if (channelId.isNullOrBlank()) {
        println("❌ Channel ID is required!")
        println("   Usage: slack-ask <channelId> <question>")
        return
    }
    
    if (question.isNullOrBlank()) {
        println("❌ Question is required!")
        println("   Usage: slack-ask <channelId> <question>")
        return
    }
    
    // Check for required env
    val botToken = System.getenv("SLACK_BOT_TOKEN")
    if (botToken.isNullOrBlank()) {
        println("❌ SLACK_BOT_TOKEN environment variable is required!")
        println("   Export it: export SLACK_BOT_TOKEN='xoxb-...'")
        return
    }
    
    // Auto-detect LLM provider: OpenAI if API key set, otherwise Ollama
    val openaiKey = System.getenv("OPENAI_API_KEY")
    val config = if (!openaiKey.isNullOrBlank()) {
        val openaiConfig = LLMProviderConfig.OpenAI.fromEnv()
        println("📡 LLM: OpenAI ${openaiConfig.model}")
        openaiConfig
    } else {
        val ollamaConfig = LLMProviderConfig.Ollama.fromEnv()
        println("📡 LLM: Ollama ${ollamaConfig.model} @ ${ollamaConfig.baseUrl}")
        ollamaConfig
    }
    
    val agent = SlackQAAgent.create(config)
    val userInput = SlackQAAgent.createAskPrompt(channelId, question)
    
    println("📋 Task: Answer question in #$channelId")
    println("❓ Question: $question")
    println()
    
    val result = agent.run(userInput)
    
    println("\n" + "=".repeat(50))
    println("📝 Agent Response:")
    println("=".repeat(50))
    println(result)
}

// ============================================================================
// Tool Testing
// ============================================================================

suspend fun runToolTest(command: String, args: Array<String>) {
    val toolName = command.removePrefix("tool:")
    
    println("🔧 Testing Tool: $toolName")
    println("=".repeat(50))
    
    val result = when (toolName) {
        "list_slack_channels" -> {
            ListSlackChannels.execute(ListSlackChannels.Args)
        }
        "fetch_slack_messages" -> {
            val channelId = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:fetch_slack_messages <channelId> [limit]")
                return
            }
            val limit = args.getOrNull(2)?.toIntOrNull() ?: 50
            FetchSlackMessages.execute(FetchSlackMessages.Args(channelId = channelId, limit = limit))
        }
        "post_slack_message" -> {
            val channelId = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:post_slack_message <channelId> <text>")
                return
            }
            val text = args.drop(2).joinToString(" ").ifBlank {
                println("❌ Usage: tool:post_slack_message <channelId> <text>")
                return
            }
            PostSlackMessage.execute(PostSlackMessage.Args(channelId = channelId, text = text))
        }
        "get_channel_info" -> {
            val channelId = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:get_channel_info <channelId>")
                return
            }
            GetChannelInfo.execute(GetChannelInfo.Args(channelId = channelId))
        }
        "get_channel_id" -> {
            val channelName = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:get_channel_id <channelName>")
                return
            }
            GetChannelIdByName.execute(GetChannelIdByName.Args(channelName = channelName))
        }
        "fetch_jira_task" -> {
            val taskKey = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:fetch_jira_task <taskKey>")
                return
            }
            FetchJiraTask.execute(FetchJiraTask.Args(taskKey = taskKey))
        }
        "search_jira" -> {
            val jql = args.drop(1).joinToString(" ").ifBlank {
                println("❌ Usage: tool:search_jira <jql>")
                return
            }
            SearchJiraTasks.execute(SearchJiraTasks.Args(jql = jql))
        }
        "fetch_github_prs" -> {
            val username = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:fetch_github_prs <username>")
                return
            }
            FetchGitHubPRs.execute(FetchGitHubPRs.Args(username = username))
        }
        "analyze_ui_tests" -> {
            AnalyzeUITestFailures.execute(AnalyzeUITestFailures.Args())
        }
        "get_figma_design" -> {
            val url = args.getOrNull(1) ?: run {
                println("❌ Usage: tool:get_figma_design <figmaUrl>")
                return
            }
            GetFigmaDesign.execute(GetFigmaDesign.Args(figmaUrl = url))
        }
        "fetch_playstore_reviews" -> {
            FetchPlayStoreReviews.execute(FetchPlayStoreReviews.Args())
        }
        "get_todays_date" -> {
            GetTodaysDate.execute(GetTodaysDate.Args)
        }
        "check_birthday" -> {
            CheckBirthday.execute(CheckBirthday.Args)
        }
        else -> {
            "❌ Unknown tool: $toolName. Run 'help' to see available tools."
        }
    }
    
    println("\n" + "=".repeat(50))
    println("📝 Tool Result:")
    println("=".repeat(50))
    println(result)
}

// ============================================================================
// Utility
// ============================================================================

fun showConfig() {
    val config = CookerConfig.fromEnv()
    config.printSummary()
}

fun showHelp(unknownCommand: String?) {
    if (unknownCommand != null && unknownCommand != "help") {
        println("❌ Unknown command: $unknownCommand\n")
    }
    
    println("""
        |Usage: ./gradlew run --args="<command>"
        |
        |🤖 AGENTS:
        |  slack-summarize <channelId> [count]   Summarize channel & post back
        |  slack-ask <channelId> <question>      Ask a question about channel history
        |  birthday                              Birthday PR Recap Agent
        |  uitest                                UI Test Analyzer Agent
        |
        |🔧 TOOLS (for testing):
        |  tool:list_slack_channels
        |  tool:fetch_slack_messages <channelId> [limit]
        |  tool:post_slack_message <channelId> <text>
        |  tool:get_channel_info <channelId>
        |  tool:fetch_jira_task <taskKey>
        |  tool:search_jira <jql>
        |  tool:fetch_github_prs <username>
        |  tool:analyze_ui_tests
        |  tool:get_figma_design <url>
        |  tool:fetch_playstore_reviews
        |  tool:get_todays_date
        |  tool:check_birthday
        |
        |⚙️ UTILITY:
        |  config                Show current configuration
        |  help                  Show this help message
        |
        |📋 ENVIRONMENT VARIABLES:
        |  OLLAMA_URL            Ollama server URL (default: http://localhost:11434)
        |  OLLAMA_MODEL          LLM model name (default: gpt-oss:20b)
        |  SLACK_BOT_TOKEN       Slack Bot Token (xoxb-...)
        |  SLACK_UI_TEST_WEBHOOK_URL  Slack webhook for notifications
        |  JIRA_BASE_URL         Jira instance URL
        |  JIRA_EMAIL            Jira email
        |  JIRA_API_TOKEN        Jira API token
        |  FIGMA_ACCESS_TOKEN    Figma personal access token
        |  GITHUB_TOKEN          GitHub token (optional, uses gh cli)
        |
        |📚 Examples:
        |  ./gradlew run --args="config"
        |  ./gradlew run --args="tool:list_slack_channels"
        |  ./gradlew run --args="tool:fetch_jira_task MOBILE-1234"
        |  ./gradlew run --args="birthday"
    """.trimMargin())
}

