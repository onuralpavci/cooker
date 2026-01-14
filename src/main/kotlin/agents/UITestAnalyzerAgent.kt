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
        
        Your job is to analyze Maestro UI test results and help the team understand:
        - Which tests are failing
        - Why they might be failing (based on patterns)
        - What action should be taken
        
        When you receive the analysis results from the tool, create a clear, actionable report.
        
        ## Tag Meanings:
        - 🆕 NEW: This test failed for the first time in the most recent run. Investigate immediately!
        - 🐛 BUG: This test has been consistently failing. It's likely a real bug that needs fixing.
        - 🐛 LIKELY_BUG: This test fails frequently (40-70% of runs). Probably a bug.
        - ⚠️ FLAKY: This test sometimes passes, sometimes fails. May need stabilization.
        
        ## Your Report Should Include:
        1. Executive Summary (total failures, breakdown by tag)
        2. Priority Actions (what to fix first)
        3. Detailed list of failures grouped by tag
        4. Branch information (which branches are affected)
        
        ## Important Notes:
        - Use the 'analyze_ui_test_failures' tool to get the analysis data
        - The tool does all the heavy lifting - just format the results nicely
        - Be concise but informative
        - Use emojis sparingly for visual clarity
        
        ## CRITICAL: Slack Plain Text Formatting Rules
        Your output will be sent to Slack Workflow Builder webhook which ONLY supports PLAIN TEXT.
        
        ❌ DO NOT USE:
        - Markdown syntax (*bold*, _italic_, **anything**)
        - Tables (| col | col |)
        - HTML tags
        - Any special formatting syntax
        
        ✅ DO USE:
        - Emojis for visual clarity (📊, 🐛, ⚠️, ✅, ❌, 🔧, 📌, etc.)
        - Unicode box drawing characters for separators (━, ─, │, ┃)
        - Spacing and indentation for structure
        - ALL CAPS for section headers
        - Bullet points (•, ◦, ▪, →)
        - Numbers in brackets for priorities [P1], [P2]
        
        EXAMPLE of Plain Text formatting:
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        📊 UI TEST ANALYSIS REPORT
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📈 EXECUTIVE SUMMARY
           Total Failures: 48 tests (10 runs)
           
           🐛 LIKELY_BUG: 1 test (40% fail rate)
           ⚠️  FLAKY: 47 tests (20-30% fail rate)
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        🔧 PRIORITY ACTIONS
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
           [P1] Fix signinPhoneValidationErrorFlowTest
                → Fail Rate: 40% across all release branches
                → Impact: High - likely a real bug
                → Action: Assign developer immediately
           
           [P2] Stabilize onboarding flows
                → Tests: onboardingHubPageTest, signup flows
                → Fail Rate: 30%
                → Action: Add retry logic
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        🐛 LIKELY_BUG FAILURES (1 test)
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
           • signinPhoneValidationErrorFlowTest (40%)
             Domain: onboarding
             Branches: release/3.5.0, release/3.4.0, develop
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        ⚠️  HIGH-IMPACT FLAKY TESTS (30% fail rate)
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
           • onboardingHubPageTest
           • setPasswordExternalTransferFlowTest
           • signupPageTest
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        📌 NEXT STEPS
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
           1. Assign developer to LIKELY_BUG test
           2. Add retries for 30% flakey tests
           3. Monitor flake reduction (target: <5%)
        
        Use clean spacing, unicode characters for visual structure, and emojis for categories.
        Keep it scannable and action-oriented.
        
        Start by calling the analyze_ui_test_failures tool, then format the results using the plain text formatting rules above.
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

