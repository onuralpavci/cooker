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
import com.avci.tools.AnalyzeUITestFailures

/**
 * AI Agent that analyzes UI test failures from GitHub Actions workflows.
 * 
 * This agent:
 * 1. Fetches the last N workflow runs for "Maestro UI Test"
 * 2. Downloads test summary artifacts
 * 3. Analyzes failure patterns
 * 4. Categorizes failures as NEW, BUG, or FLAKY
 * 5. Generates a human-readable report
 */
object UITestAnalyzerAgent {

    private val systemPrompt = """
        You are a UI Test Failure Analyzer for a mobile development team.
        
        Your job is to analyze Maestro UI test results and provide a concise, actionable report.
        
        ## Tag Meanings:
        - 🆕 NEW: First-time failure in the most recent run
        - 🐛 BUG: Always failing (100% fail rate)
        - 🐛 LIKELY_BUG: Fails frequently (40-70% of runs)
        - ⚠️ FLAKY: Intermittent failures (<40%)
        
        ## CRITICAL: Keep it SHORT and ACTIONABLE
        
        Your report MUST be concise. Format:
        
        1. List of failed tests with tags and fail rates
        2. Brief summary (4-5 sentences max)
        
        That's it. No long explanations, no detailed breakdowns, no tables.
        
        ## Output Format (Plain Text for Slack):
        
        🧪 FAILED TESTS (total count)
        
        🐛 LIKELY_BUG (count)
           • testName1 (fail rate)
           • testName2 (fail rate)
        
        ⚠️ FLAKY - High Impact (count if >25% fail rate)
           • testName3 (fail rate)
           • testName4 (fail rate)
        
        ⚠️ FLAKY - Medium Impact (count if 10-25% fail rate)
           • testName5 (fail rate)
           • testName6 (fail rate)
           ... (show first 5-10, then say "+ X more")
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        📝 SUMMARY
        
        [Write 4-5 short sentences covering:]
        - Most critical issue (LIKELY_BUG or high-fail tests)
        - Main flakiness pattern (which domain/area)
        - Recommended action priority
        - Any notable branch impacts
        
        EXAMPLE:
        
        🧪 FAILED TESTS (48 total)
        
        🐛 LIKELY_BUG (1)
           • signinPhoneValidationErrorFlowTest (40%)
        
        ⚠️ FLAKY - High Impact (6)
           • onboardingHubPageTest (30%)
           • setPasswordExternalTransferFlowTest (30%)
           • signupPageTest (30%)
           • setPasswordInternalTransferFlowTest (30%)
           • signupExternalTransferFlowTest (30%)
           • signupInternalTransferFlowTest (30%)
        
        ⚠️ FLAKY - Medium Impact (41)
           • explorePageTest (20%)
           • marketsPageTest (20%)
           • homePageTest (20%)
           • menuPageTest (20%)
           • portfolioVisibilityToggleFlowTest (20%)
           + 36 more
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        📝 SUMMARY
        
        1 critical bug (signinPhoneValidationErrorFlowTest) needs immediate attention - failing 40% across all release branches. 6 onboarding tests are highly flaky (30%) and need retry logic added. 41 tests show medium flakiness (20%) in crypto/trade domains. Focus: Fix the bug first, then stabilize onboarding flows on release branches.
        
        RULES:
        - Keep summary under 5 sentences
        - Be direct and actionable
        - No redundant information
        - Use emojis only in headers
        - List tests alphabetically within each category
        
        Start by calling the analyze_ui_test_failures tool, then format the results.
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
            tool(AnalyzeUITestFailures)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(id = "ui-test-analyzer-agent", params = LLMParams(temperature = config.temperature)) {
                system(content = systemPrompt)
            },
            model = llm,
            maxAgentIterations = 10
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
}

