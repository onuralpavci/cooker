package com.avci.tools.github

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.CommandRunner
import kotlinx.serialization.Serializable

/**
 * Tool that fetches recent merged Pull Requests for a GitHub user.
 * Uses GitHub CLI (gh) for authentication.
 */
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
        println("🔧 [TOOL] fetch_github_prs(username=\"${args.username}\", limit=${args.limit})")
        
        if (args.username.isBlank()) {
            return logAndReturn("❌ Error: username is required")
        }
        
        val command = listOf(
            "gh", "search", "prs",
            "--author", args.username,
            "--merged",
            "--limit", args.limit.toString(),
            "--json", "number,title,repository,createdAt,updatedAt,url"
        )
        
        val result = CommandRunner.run(command, timeoutSeconds = 30)
        
        return if (result.isSuccess) {
            if (result.output.isEmpty() || result.output == "[]") {
                logAndReturn("No merged PRs found for user '${args.username}'")
            } else {
                logAndReturn("✅ Found PRs for ${args.username}:\n${result.output}")
            }
        } else {
            logAndReturn("❌ Error: ${result.output}")
        }
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

