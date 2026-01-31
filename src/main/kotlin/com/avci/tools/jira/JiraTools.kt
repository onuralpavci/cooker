package com.avci.tools.jira

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * Jira API Tools for task management.
 * 
 * Uses Jira REST API v3 with Basic Auth (email + API token).
 * 
 * Environment variables:
 * - JIRA_BASE_URL: Your Jira instance URL (e.g., https://yourcompany.atlassian.net)
 * - JIRA_EMAIL: Your Atlassian email
 * - JIRA_API_TOKEN: API token from https://id.atlassian.com/manage-profile/security/api-tokens
 */
object FetchJiraTask : SimpleTool<FetchJiraTask.Args>(
    argsSerializer = Args.serializer(),
    name = "fetch_jira_task",
    description = "Fetches details of a Jira task by its key (e.g., MOBILE-1234). Returns summary, description, status, assignee, and linked Figma URLs."
) {

    @Serializable
    data class Args(
        val taskKey: String = ""
    )

    @Serializable
    data class JiraTask(
        val key: String,
        val summary: String,
        val description: String?,
        val status: String,
        val assignee: String?,
        val reporter: String?,
        val priority: String?,
        val labels: List<String>,
        val figmaLinks: List<String>,
        val createdAt: String,
        val updatedAt: String
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] fetch_jira_task(taskKey=\"${args.taskKey}\")")
        
        val baseUrl = System.getenv("JIRA_BASE_URL")
        val email = System.getenv("JIRA_EMAIL")
        val apiToken = System.getenv("JIRA_API_TOKEN")
        
        if (baseUrl.isNullOrBlank() || email.isNullOrBlank() || apiToken.isNullOrBlank()) {
            return logAndReturn("""
                ❌ Jira configuration missing. Set these environment variables:
                - JIRA_BASE_URL (e.g., https://yourcompany.atlassian.net)
                - JIRA_EMAIL
                - JIRA_API_TOKEN (from id.atlassian.com/manage-profile/security/api-tokens)
            """.trimIndent())
        }
        
        if (args.taskKey.isBlank()) {
            return logAndReturn("❌ taskKey is required (e.g., MOBILE-1234)")
        }
        
        println("   └─ Jira URL: $baseUrl")
        println("   └─ Email: $email")
        
        // Create Basic Auth header
        val auth = Base64.getEncoder().encodeToString("$email:$apiToken".toByteArray())
        
        val url = "$baseUrl/rest/api/3/issue/${args.taskKey}"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf(
                "Authorization" to "Basic $auth",
                "Accept" to "application/json"
            )
        )
        
        if (!response.isSuccess) {
            return logAndReturn("❌ HTTP ${response.statusCode}: ${response.body.take(200)}")
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val issue = json.parseToJsonElement(response.body).jsonObject
            val fields = issue["fields"]?.jsonObject
            
            // Extract Figma links from description
            val description = fields?.get("description")?.toString() ?: ""
            val figmaLinks = extractFigmaLinks(description)
            
            val task = JiraTask(
                key = issue["key"]?.jsonPrimitive?.content ?: args.taskKey,
                summary = fields?.get("summary")?.jsonPrimitive?.content ?: "",
                description = simplifyDescription(description),
                status = fields?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "",
                assignee = fields?.get("assignee")?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull,
                reporter = fields?.get("reporter")?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull,
                priority = fields?.get("priority")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull,
                labels = fields?.get("labels")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                figmaLinks = figmaLinks,
                createdAt = fields?.get("created")?.jsonPrimitive?.content ?: "",
                updatedAt = fields?.get("updated")?.jsonPrimitive?.content ?: ""
            )
            
            println("   └─ Task: ${task.summary}")
            println("   └─ Status: ${task.status}")
            println("   └─ Figma links: ${figmaLinks.size}")
            
            json.encodeToString(JiraTask.serializer(), task)
            
        } catch (e: Exception) {
            logAndReturn("❌ Parse error: ${e.message}")
        }
    }
    
    private fun extractFigmaLinks(text: String): List<String> {
        val figmaRegex = Regex("""https://(?:www\.)?figma\.com/(?:file|design)/[a-zA-Z0-9]+[^\s"'<>)*\]]*""")
        return figmaRegex.findAll(text).map { it.value }.distinct().toList()
    }
    
    private fun simplifyDescription(description: String): String {
        // Jira uses Atlassian Document Format (ADF) - simplify for LLM
        return description
            .replace(Regex("""\\n"""), "\n")
            .replace(Regex(""""type":"[^"]+""""), "")
            .replace(Regex(""""attrs":\{[^}]*\}"""), "")
            .take(2000)
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

/**
 * Tool to create a new Jira task.
 */
object CreateJiraTask : SimpleTool<CreateJiraTask.Args>(
    argsSerializer = Args.serializer(),
    name = "create_jira_task",
    description = "Creates a new Jira task. Requires project key, summary, and optionally description, labels, and priority."
) {

    @Serializable
    data class Args(
        val projectKey: String = "",
        val summary: String = "",
        val description: String = "",
        val issueType: String = "Task",
        val labels: List<String> = emptyList(),
        val priority: String = "Medium"
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] create_jira_task(project=\"${args.projectKey}\", summary=\"${args.summary.take(30)}...\")")
        
        val baseUrl = System.getenv("JIRA_BASE_URL")
        val email = System.getenv("JIRA_EMAIL")
        val apiToken = System.getenv("JIRA_API_TOKEN")
        
        if (baseUrl.isNullOrBlank() || email.isNullOrBlank() || apiToken.isNullOrBlank()) {
            return "❌ Jira configuration missing"
        }
        
        if (args.projectKey.isBlank() || args.summary.isBlank()) {
            return "❌ projectKey and summary are required"
        }
        
        val auth = Base64.getEncoder().encodeToString("$email:$apiToken".toByteArray())
        
        // Build request body
        val requestBody = buildJsonObject {
            putJsonObject("fields") {
                putJsonObject("project") { put("key", args.projectKey) }
                put("summary", args.summary)
                putJsonObject("issuetype") { put("name", args.issueType) }
                if (args.description.isNotBlank()) {
                    putJsonObject("description") {
                        put("type", "doc")
                        put("version", 1)
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "paragraph")
                                putJsonArray("content") {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", args.description)
                                    }
                                }
                            }
                        }
                    }
                }
                if (args.labels.isNotEmpty()) {
                    putJsonArray("labels") {
                        args.labels.forEach { add(it) }
                    }
                }
            }
        }
        
        val response = HttpClient.post(
            url = "$baseUrl/rest/api/3/issue",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Basic $auth",
                "Content-Type" to "application/json"
            )
        )
        
        return if (response.isSuccess) {
            val json = Json { ignoreUnknownKeys = true }
            val result = json.parseToJsonElement(response.body).jsonObject
            val key = result["key"]?.jsonPrimitive?.content ?: "unknown"
            println("   └─ ✅ Created: $key")
            "✅ Created Jira task: $key ($baseUrl/browse/$key)"
        } else {
            println("   └─ ❌ Failed: ${response.body.take(200)}")
            "❌ Failed to create task: ${response.body}"
        }
    }
}

/**
 * Tool to search Jira tasks with JQL.
 */
object SearchJiraTasks : SimpleTool<SearchJiraTasks.Args>(
    argsSerializer = Args.serializer(),
    name = "search_jira_tasks",
    description = "Searches Jira tasks using JQL query. Returns matching tasks with key, summary, and status."
) {

    @Serializable
    data class Args(
        val jql: String = "",
        val maxResults: Int = 20
    )

    @Serializable
    data class SearchResult(
        val total: Int,
        val tasks: List<TaskSummary>
    )

    @Serializable
    data class TaskSummary(
        val key: String,
        val summary: String,
        val status: String,
        val assignee: String?
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] search_jira_tasks(jql=\"${args.jql.take(50)}...\", maxResults=${args.maxResults})")
        
        val baseUrl = System.getenv("JIRA_BASE_URL")
        val email = System.getenv("JIRA_EMAIL")
        val apiToken = System.getenv("JIRA_API_TOKEN")
        
        if (baseUrl.isNullOrBlank() || email.isNullOrBlank() || apiToken.isNullOrBlank()) {
            return "❌ Jira configuration missing"
        }
        
        if (args.jql.isBlank()) {
            return "❌ JQL query is required"
        }
        
        val auth = Base64.getEncoder().encodeToString("$email:$apiToken".toByteArray())
        val encodedJql = java.net.URLEncoder.encode(args.jql, "UTF-8")
        
        val url = "$baseUrl/rest/api/3/search?jql=$encodedJql&maxResults=${args.maxResults}&fields=summary,status,assignee"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf(
                "Authorization" to "Basic $auth",
                "Accept" to "application/json"
            )
        )
        
        if (!response.isSuccess) {
            return "❌ HTTP ${response.statusCode}: ${response.body.take(200)}"
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val data = json.parseToJsonElement(response.body).jsonObject
            
            val total = data["total"]?.jsonPrimitive?.intOrNull ?: 0
            val issues = data["issues"]?.jsonArray ?: JsonArray(emptyList())
            
            val tasks = issues.mapNotNull { issue ->
                val obj = issue.jsonObject
                val fields = obj["fields"]?.jsonObject ?: return@mapNotNull null
                
                TaskSummary(
                    key = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    summary = fields["summary"]?.jsonPrimitive?.content ?: "",
                    status = fields["status"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "",
                    assignee = fields["assignee"]?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull
                )
            }
            
            println("   └─ Found ${tasks.size} of $total total matches")
            
            json.encodeToString(SearchResult.serializer(), SearchResult(total, tasks))
            
        } catch (e: Exception) {
            "❌ Parse error: ${e.message}"
        }
    }
}

