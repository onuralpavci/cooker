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
    description = "Analyzes UI test failures from the last N GitHub Actions workflow runs. Automatically discovers and downloads all maestro-test-summary-* artifacts. Returns a JSON report with failure patterns categorized as NEW (first time failure), BUG (consistent failure), or FLAKY (intermittent failure)."
) {

    @Serializable
    data class Args(
        val repo: String = "midas-engineering/mobile-android",
        val workflow: String = "Maestro UI Test",
        val runCount: Int = 10
    )

    // Internal data class for workflow runs (not serialized to JSON)
    private data class WorkflowRun(
        val id: String,
        val createdAt: String,
        val headBranch: String,
        val conclusion: String
    )

    @Serializable
    data class TestFailure(
        val testName: String,
        val tag: String,
        val failRate: String  // e.g., "40%"
        // Removed 'domain' and 'branches' - not needed for minimal report format
    )

    @Serializable
    data class AnalysisResult(
        val analysisDate: String,
        val runsAnalyzed: Int,
        val summary: Map<String, Int>,
        val failures: List<TestFailure>
        // Removed 'runs' - LLM doesn't need full run details, just the count
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
                println("      📁 Temp dir: ${tempDir.absolutePath}")
                println("      🔍 Using wildcard pattern: maestro-test-summary-*")
                
                val testFailures = mutableMapOf<String, MutableList<Pair<Int, WorkflowRun>>>() // testName -> [(runIndex, run)]
                var totalArtifactsProcessed = 0
                var totalArtifactsWithFailures = 0
                var totalFailuresFound = 0
                
                runs.forEachIndexed { index, run ->
                    // Download all maestro-test-summary-* artifacts for this run
                    val (domainFailures, artifactCount) = downloadAndParseAllTestSummaries(args.repo, run.id, tempDir)
                    totalArtifactsProcessed += artifactCount
                    
                    for ((domain, failures) in domainFailures) {
                        if (failures.isNotEmpty()) {
                            totalArtifactsWithFailures++
                            totalFailuresFound += failures.size
                            println("      📥 Run ${run.id}/$domain: ${failures.size} failures")
                        }
                        for (testName in failures) {
                            testFailures.getOrPut(testName) { mutableListOf() }.add(index to run)
                        }
                    }
                }
                
                println("      📊 Total: $totalArtifactsProcessed artifacts processed, $totalArtifactsWithFailures with failures, $totalFailuresFound total test failures")
                
                println("   └─ Found ${testFailures.size} unique failed tests")
                
                // Step 3: Categorize failures
                val categorizedFailures = testFailures.map { (testName, failureList) ->
                    val failIndices = failureList.map { it.first }.toSet()
                    val failCount = failIndices.size
                    
                    val tag = when {
                        failIndices == setOf(0) -> "NEW" // Only failed in latest run
                        failCount >= (runs.size * 0.7) -> "BUG" // Failed in 70%+ of runs
                        failCount >= (runs.size * 0.4) -> "LIKELY_BUG" // Failed in 40-70% of runs
                        else -> "FLAKY" // Intermittent failures
                    }
                    
                    val failRate = "${(failCount * 100) / runs.size}%"
                    
                    TestFailure(
                        testName = testName,
                        tag = tag,
                        failRate = failRate
                    )
                }.sortedWith(compareBy({ tagPriority(it.tag) }, { -(it.failRate.removeSuffix("%").toIntOrNull() ?: 0) }, { it.testName }))
                
                // Step 4: Create summary
                val summary = mapOf(
                    "NEW" to categorizedFailures.count { it.tag == "NEW" },
                    "BUG" to categorizedFailures.count { it.tag == "BUG" },
                    "LIKELY_BUG" to categorizedFailures.count { it.tag == "LIKELY_BUG" },
                    "FLAKY" to categorizedFailures.count { it.tag == "FLAKY" }
                )
                
                // Step 5: Build result (minimal for LLM efficiency)
                val result = AnalysisResult(
                    analysisDate = java.time.LocalDate.now().toString(),
                    runsAnalyzed = runs.size,
                    summary = summary,
                    failures = categorizedFailures
                )
                
                // Log run breakdown for verification
                val successCount = runs.count { it.conclusion == "success" }
                val failureCount = runs.count { it.conclusion == "failure" }
                println("      📊 Runs breakdown: $successCount success, $failureCount failure (total: ${runs.size})")
                
                val json = Json { prettyPrint = true }
                val resultJson = json.encodeToString(AnalysisResult.serializer(), result)
                
                println("   └─ Analysis complete!")
                println("      📄 JSON size: ${resultJson.length} chars, ${result.failures.size} failures categorized")
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
    
    /**
     * Downloads all maestro-test-summary-* artifacts for a given run and parses them.
     * Returns a pair of (domain -> list of failed test names, artifact count).
     */
    private fun downloadAndParseAllTestSummaries(repo: String, runId: String, tempDir: File): Pair<Map<String, List<String>>, Int> {
        val outputDir = File(tempDir, runId)
        outputDir.mkdirs()
        
        val command = listOf(
            "gh", "run", "download", runId,
            "--repo", repo,
            "--pattern", "maestro-test-summary-*",
            "--dir", outputDir.absolutePath
        )
        
        runCommand(command, ignoreErrors = true)
        
        // Find all maestro-test-summary-* directories
        val domainFailures = mutableMapOf<String, List<String>>()
        
        val subdirs = outputDir.listFiles { file -> 
            file.isDirectory && file.name.startsWith("maestro-test-summary-")
        } ?: emptyArray()
        
        val artifactCount = subdirs.size
        
        for (domainDir in subdirs) {
            // Extract domain name: "maestro-test-summary-crypto" -> "crypto"
            val domain = domainDir.name.removePrefix("maestro-test-summary-")
            
            // Find summary.txt file
            val summaryFile = domainDir.listFiles()?.firstOrNull { it.name.endsWith(".txt") }
            
            if (summaryFile == null) {
                // No summary file - artifact might be empty or success run
                continue
            }
            
            val failures = parseSummaryFile(summaryFile)
            if (failures.isNotEmpty()) {
                domainFailures[domain] = failures
            }
        }
        
        return Pair(domainFailures, artifactCount)
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

