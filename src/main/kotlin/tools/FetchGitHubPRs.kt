package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object FetchGitHubPRs : SimpleTool<FetchGitHubPRs.Args>(
    argsSerializer = Args.serializer(),
    name = "fetch_github_prs",
    description = "Fetches the recent merged Pull Requests for a GitHub user using GitHub CLI. Returns PR details including title, repository, and merge date."
) {

    @Serializable
    data class Args(
        val username: String = "",
        val limit: Int = 10
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] fetch_github_prs(username=\"${args.username}\", limit=${args.limit})")
        
        if (args.username.isBlank()) {
            val result = "Error: username is required. Please provide a GitHub username to fetch PRs."
            println("   └─ Result: $result")
            return result
        }
        
        return try {
            // Use 'gh search prs' which works globally without needing to be in a git repo
            val command = listOf(
                "gh", "search", "prs",
                "--author", args.username,
                "--merged",
                "--limit", args.limit.toString(),
                "--json", "number,title,repository,createdAt,updatedAt,url"
            )
            
            println("   └─ Executing: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val exitCode = process.waitFor(30, TimeUnit.SECONDS)
            
            if (!exitCode) {
                process.destroyForcibly()
                val result = "Error: GitHub CLI command timed out after 30 seconds"
                println("   └─ Result: $result")
                return result
            }
            
            if (process.exitValue() != 0) {
                val result = "Error executing GitHub CLI: ${output.toString().trim()}"
                println("   └─ Result: $result")
                return result
            }

            val result = output.toString().trim()
            if (result.isEmpty() || result == "[]") {
                val msg = "No merged PRs found for user '${args.username}'. They might not have any merged PRs recently."
                println("   └─ Result: $msg")
                msg
            } else {
                println("   └─ Result: Found PRs (${result.length} chars)")
                "Successfully fetched recent merged PRs for ${args.username}:\n$result"
            }
        } catch (e: Exception) {
            val result = "Error fetching GitHub PRs: ${e.message}. Make sure GitHub CLI (gh) is installed and authenticated."
            println("   └─ Result: $result")
            result
        }
    }
}
