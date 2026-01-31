package com.avci.tools.github

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.CommandRunner
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

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
    description = "Analyzes UI test failures from the last N GitHub Actions workflow runs. Returns a JSON report with failure patterns categorized as NEW, BUG, or FLAKY."
) {

    @Serializable
    data class Args(
        val repo: String = "midas-engineering/mobile-android",
        val workflow: String = "Maestro UI Test",
        val runCount: Int = 10
    )

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
        val failRate: String
    )

    @Serializable
    data class AnalysisResult(
        val analysisDate: String,
        val runsAnalyzed: Int,
        val summary: Map<String, Int>,
        val failures: List<TestFailure>
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] analyze_ui_test_failures(repo=\"${args.repo}\", workflow=\"${args.workflow}\", runCount=${args.runCount})")
        
        return try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "uitest-${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // Step 1: Fetch workflow runs
                println("   └─ Fetching workflow runs...")
                val runs = fetchWorkflowRuns(args.repo, args.workflow, args.runCount)
                
                if (runs.isEmpty()) {
                    return "❌ No workflow runs found for '${args.workflow}'"
                }
                println("   └─ Found ${runs.size} runs")
                
                // Step 2: Download and parse test summaries
                println("   └─ Downloading test summaries...")
                val testFailures = mutableMapOf<String, MutableList<Pair<Int, WorkflowRun>>>()
                
                runs.forEachIndexed { index, run ->
                    val domainFailures = downloadAndParseTestSummaries(args.repo, run.id, tempDir)
                    for ((domain, failures) in domainFailures) {
                        if (failures.isNotEmpty()) {
                            println("      📥 Run ${run.id}/$domain: ${failures.size} failures")
                        }
                        for (testName in failures) {
                            testFailures.getOrPut(testName) { mutableListOf() }.add(index to run)
                        }
                    }
                }
                
                println("   └─ Found ${testFailures.size} unique failed tests")
                
                // Step 3: Categorize failures
                val categorizedFailures = testFailures.map { (testName, failureList) ->
                    val failIndices = failureList.map { it.first }.toSet()
                    val failCount = failIndices.size
                    
                    val tag = when {
                        failIndices == setOf(0) -> "NEW"
                        failCount >= (runs.size * 0.7) -> "BUG"
                        failCount >= (runs.size * 0.4) -> "LIKELY_BUG"
                        else -> "FLAKY"
                    }
                    
                    TestFailure(
                        testName = testName,
                        tag = tag,
                        failRate = "${(failCount * 100) / runs.size}%"
                    )
                }.sortedWith(compareBy({ tagPriority(it.tag) }, { it.testName }))
                
                // Step 4: Create result
                val result = AnalysisResult(
                    analysisDate = java.time.LocalDate.now().toString(),
                    runsAnalyzed = runs.size,
                    summary = mapOf(
                        "NEW" to categorizedFailures.count { it.tag == "NEW" },
                        "BUG" to categorizedFailures.count { it.tag == "BUG" },
                        "LIKELY_BUG" to categorizedFailures.count { it.tag == "LIKELY_BUG" },
                        "FLAKY" to categorizedFailures.count { it.tag == "FLAKY" }
                    ),
                    failures = categorizedFailures
                )
                
                val json = Json { prettyPrint = true }
                val resultJson = json.encodeToString(AnalysisResult.serializer(), result)
                
                println("   └─ ✅ Analysis complete! ${result.failures.size} failures categorized")
                resultJson
                
            } finally {
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            "❌ Error: ${e.message}"
        }
    }
    
    private fun tagPriority(tag: String) = when (tag) {
        "NEW" -> 0; "BUG" -> 1; "LIKELY_BUG" -> 2; "FLAKY" -> 3; else -> 4
    }
    
    private fun fetchWorkflowRuns(repo: String, workflow: String, limit: Int): List<WorkflowRun> {
        val result = CommandRunner.run(listOf(
            "gh", "run", "list",
            "--repo", repo,
            "--workflow", workflow,
            "--limit", limit.toString(),
            "--json", "databaseId,createdAt,headBranch,conclusion"
        ))
        
        if (!result.isSuccess || result.output.isBlank()) return emptyList()
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            json.parseToJsonElement(result.output).jsonArray.map { element ->
                val obj = element.jsonObject
                WorkflowRun(
                    id = obj["databaseId"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["createdAt"]?.jsonPrimitive?.content ?: "",
                    headBranch = obj["headBranch"]?.jsonPrimitive?.content ?: "",
                    conclusion = obj["conclusion"]?.jsonPrimitive?.content ?: ""
                )
            }
        } catch (e: Exception) {
            println("      ⚠️ Parse error: ${e.message}")
            emptyList()
        }
    }
    
    private fun downloadAndParseTestSummaries(repo: String, runId: String, tempDir: File): Map<String, List<String>> {
        val outputDir = File(tempDir, runId)
        outputDir.mkdirs()
        
        CommandRunner.run(listOf(
            "gh", "run", "download", runId,
            "--repo", repo,
            "--pattern", "maestro-test-summary-*",
            "--dir", outputDir.absolutePath
        ), ignoreErrors = true)
        
        val domainFailures = mutableMapOf<String, List<String>>()
        
        outputDir.listFiles { file -> 
            file.isDirectory && file.name.startsWith("maestro-test-summary-")
        }?.forEach { domainDir ->
            val domain = domainDir.name.removePrefix("maestro-test-summary-")
            val summaryFile = domainDir.listFiles()?.firstOrNull { it.name.endsWith(".txt") }
            
            summaryFile?.let {
                val failures = parseSummaryFile(it)
                if (failures.isNotEmpty()) {
                    domainFailures[domain] = failures
                }
            }
        }
        
        return domainFailures
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
                val testName = line.trim()
                    .removePrefix("-").trim()
                    .split(" ")[0].split("(")[0].trim()
                if (testName.isNotEmpty()) failures.add(testName)
            }
        }
        
        return failures
    }
}

