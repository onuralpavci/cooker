package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Tool that analyzes UI test failures from GitHub Actions workflows.
 * 
 * This tool:
 * 1. Fetches the last N workflow runs for "Maestro UI Test"
 * 2. Downloads test summary artifacts for each domain
 * 3. Parses failed tests from summaries
 * 4. Categorizes failures as NEW, BUG, LIKELY_BUG, or FLAKY
 * 5. Returns a structured JSON report
 */
object AnalyzeUITestFailures : SimpleTool<AnalyzeUITestFailures.Args>(
    argsSerializer = Args.serializer(),
    name = "analyze_ui_test_failures",
    description = "Analyzes UI test failures from the last N GitHub Actions workflow runs. Returns a JSON report with failure patterns categorized as NEW (first time failure), BUG (consistent failure), or FLAKY (intermittent failure)."
) {

    @Serializable
    data class Args(
        val repo: String = "midas-engineering/mobile-android",
        val workflow: String = "Maestro UI Test",
        val runCount: Int = 10,
        val domains: List<String> = listOf("crypto", "trade", "onboarding")
    )

    @Serializable
    data class WorkflowRun(
        val id: String,
        val createdAt: String,
        val headBranch: String,
        val conclusion: String
    )

    @Serializable
    data class TestFailure(
        val testName: String,
        val domain: String,
        val tag: String,
        val failCount: Int,
        val totalRuns: Int,
        val failedInRuns: List<String>,
        val branches: List<String>
    )

    @Serializable
    data class AnalysisResult(
        val analysisDate: String,
        val runsAnalyzed: Int,
        val summary: Map<String, Int>,
        val runs: List<WorkflowRun>,
        val failures: List<TestFailure>
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] analyze_ui_test_failures(repo=\"${args.repo}\", workflow=\"${args.workflow}\", runCount=${args.runCount})")
        
        return try {
            // Create temp directory for artifacts
            val tempDir = File(System.getProperty("java.io.tmpdir"), "uitest-analysis-${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // Step 1: Fetch workflow runs
                println("   └─ Fetching workflow runs...")
                val runs = fetchWorkflowRuns(args.repo, args.workflow, args.runCount)
                
                if (runs.isEmpty()) {
                    return "Error: No workflow runs found for '${args.workflow}' in ${args.repo}"
                }
                println("   └─ Found ${runs.size} runs")
                
                // Step 2: Download and parse test summaries
                println("   └─ Downloading test summaries...")
                val testFailures = mutableMapOf<String, MutableList<Pair<Int, WorkflowRun>>>() // testName -> [(runIndex, run)]
                val testDomains = mutableMapOf<String, String>() // testName -> domain
                
                runs.forEachIndexed { index, run ->
                    for (domain in args.domains) {
                        val failures = downloadAndParseTestSummary(args.repo, run.id, domain, tempDir)
                        for (testName in failures) {
                            testFailures.getOrPut(testName) { mutableListOf() }.add(index to run)
                            testDomains[testName] = domain
                        }
                    }
                }
                
                println("   └─ Found ${testFailures.size} unique failed tests")
                
                // Step 3: Categorize failures
                val categorizedFailures = testFailures.map { (testName, failureList) ->
                    val failIndices = failureList.map { it.first }.toSet()
                    val failCount = failIndices.size
                    val branches = failureList.map { it.second.headBranch }.distinct()
                    val failedRunIds = failureList.map { it.second.id }.distinct()
                    
                    val tag = when {
                        failIndices == setOf(0) -> "NEW" // Only failed in latest run
                        failCount >= (runs.size * 0.7) -> "BUG" // Failed in 70%+ of runs
                        failCount >= (runs.size * 0.4) -> "LIKELY_BUG" // Failed in 40-70% of runs
                        else -> "FLAKY" // Intermittent failures
                    }
                    
                    TestFailure(
                        testName = testName,
                        domain = testDomains[testName] ?: "unknown",
                        tag = tag,
                        failCount = failCount,
                        totalRuns = runs.size,
                        failedInRuns = failedRunIds,
                        branches = branches
                    )
                }.sortedWith(compareBy({ tagPriority(it.tag) }, { -it.failCount }, { it.testName }))
                
                // Step 4: Create summary
                val summary = mapOf(
                    "NEW" to categorizedFailures.count { it.tag == "NEW" },
                    "BUG" to categorizedFailures.count { it.tag == "BUG" },
                    "LIKELY_BUG" to categorizedFailures.count { it.tag == "LIKELY_BUG" },
                    "FLAKY" to categorizedFailures.count { it.tag == "FLAKY" }
                )
                
                // Step 5: Build result
                val result = AnalysisResult(
                    analysisDate = java.time.LocalDate.now().toString(),
                    runsAnalyzed = runs.size,
                    summary = summary,
                    runs = runs,
                    failures = categorizedFailures
                )
                
                val json = Json { prettyPrint = true }
                val resultJson = json.encodeToString(AnalysisResult.serializer(), result)
                
                println("   └─ Analysis complete!")
                resultJson
                
            } finally {
                // Cleanup temp directory
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            val error = "Error analyzing UI test failures: ${e.message}"
            println("   └─ $error")
            error
        }
    }
    
    private fun tagPriority(tag: String): Int = when (tag) {
        "NEW" -> 0
        "BUG" -> 1
        "LIKELY_BUG" -> 2
        "FLAKY" -> 3
        else -> 4
    }
    
    private fun fetchWorkflowRuns(repo: String, workflow: String, limit: Int): List<WorkflowRun> {
        val command = listOf(
            "gh", "run", "list",
            "--repo", repo,
            "--workflow", workflow,
            "--limit", limit.toString(),
            "--json", "databaseId,createdAt,headBranch,conclusion"
        )
        
        println("      📋 Command: ${command.joinToString(" ")}")
        
        val output = runCommand(command)
        
        if (output == null) {
            println("      ❌ Command returned null (failed or timed out)")
            return emptyList()
        }
        
        if (output.isBlank()) {
            println("      ⚠️ Command returned empty output")
            return emptyList()
        }
        
        // Check for error messages in output
        if (output.contains("error") || output.contains("Error") || output.contains("not found")) {
            println("      ❌ Command error: $output")
            return emptyList()
        }
        
        println("      📄 Raw output (first 200 chars): ${output.take(200)}")
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonArray = json.parseToJsonElement(output).jsonArray
            
            val runs = jsonArray.map { element ->
                val obj = element.jsonObject
                WorkflowRun(
                    id = obj["databaseId"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["createdAt"]?.jsonPrimitive?.content ?: "",
                    headBranch = obj["headBranch"]?.jsonPrimitive?.content ?: "",
                    conclusion = obj["conclusion"]?.jsonPrimitive?.content ?: ""
                )
            }
            println("      ✅ Parsed ${runs.size} workflow runs")
            runs
        } catch (e: Exception) {
            println("      ⚠️ Error parsing workflow runs: ${e.message}")
            println("      📄 Full output: $output")
            emptyList()
        }
    }
    
    private fun downloadAndParseTestSummary(repo: String, runId: String, domain: String, tempDir: File): List<String> {
        val outputDir = File(tempDir, "$runId/$domain")
        outputDir.mkdirs()
        
        val command = listOf(
            "gh", "run", "download", runId,
            "--repo", repo,
            "--name", "maestro-test-summary-$domain",
            "--dir", outputDir.absolutePath
        )
        
        runCommand(command, ignoreErrors = true)
        
        // Parse summary file
        val summaryFile = outputDir.listFiles()?.firstOrNull { it.name.endsWith(".txt") }
            ?: return emptyList()
        
        return parseSummaryFile(summaryFile)
    }
    
    private fun parseSummaryFile(file: File): List<String> {
        val failures = mutableListOf<String>()
        var inFailedSection = false
        
        file.readLines().forEach { line ->
            if (line.contains("Failed tests:")) {
                inFailedSection = true
                return@forEach
            }
            
            if (inFailedSection && line.trim().startsWith("-")) {
                // Extract test name: "  - testName (APP_TYPE=app)" -> "testName"
                val testName = line.trim()
                    .removePrefix("-")
                    .trim()
                    .split(" ")[0]
                    .split("(")[0]
                    .trim()
                
                if (testName.isNotEmpty()) {
                    failures.add(testName)
                }
            }
        }
        
        return failures
    }
    
    private fun runCommand(command: List<String>, ignoreErrors: Boolean = false): String? {
        return try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                println("      ⏱️ Command timed out after 60 seconds")
                return null
            }
            
            val exitCode = process.exitValue()
            if (exitCode != 0 && !ignoreErrors) {
                println("      ❌ Command exit code: $exitCode")
                println("      📄 Output: ${output.toString().trim()}")
                return null
            }
            
            output.toString().trim()
        } catch (e: Exception) {
            println("      ⚠️ Command exception: ${e.javaClass.simpleName}: ${e.message}")
            if (!ignoreErrors) {
                e.printStackTrace()
            }
            null
        }
    }
}

