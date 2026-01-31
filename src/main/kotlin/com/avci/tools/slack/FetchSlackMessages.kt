package com.avci.tools.slack

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Tool that fetches messages from a Slack channel.
 * 
 * Uses Slack Web API with Bot Token authentication.
 * 
 * Required Slack Bot Token Scopes:
 * - channels:history (public channels)
 * - groups:history (private channels)
 * - im:history (direct messages)
 * - channels:read (to list channels)
 * 
 * Environment variables:
 * - SLACK_BOT_TOKEN: Bot User OAuth Token (xoxb-...)
 */
object FetchSlackMessages : SimpleTool<FetchSlackMessages.Args>(
    argsSerializer = Args.serializer(),
    name = "fetch_slack_messages",
    description = "Fetches recent messages from a Slack channel. Requires channel ID and optional limit. Returns messages with author and timestamp."
) {

    private const val SLACK_API_BASE = "https://slack.com/api"

    @Serializable
    data class Args(
        val channelId: String = "",
        val limit: Int = 50,
        val includeReplies: Boolean = false,
        val excludeBotMessages: Boolean = true  // Filter out bot/system messages
    )

    @Serializable
    data class SlackMessage(
        val user: String,
        val text: String,
        val timestamp: String,
        val threadTs: String? = null
    )

    @Serializable
    data class FetchResult(
        val channelId: String,
        val messageCount: Int,
        val messages: List<SlackMessage>
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] fetch_slack_messages(channelId=\"${args.channelId}\", limit=${args.limit})")
        
        val botToken = System.getenv("SLACK_BOT_TOKEN")
        
        if (botToken.isNullOrBlank()) {
            return logAndReturn("❌ SLACK_BOT_TOKEN not set. Get it from: api.slack.com/apps → OAuth & Permissions")
        }
        
        if (args.channelId.isBlank()) {
            return logAndReturn("❌ channelId is required. Use list_slack_channels to find channel IDs.")
        }
        
        println("   └─ Using Bot Token: ${botToken.take(15)}...")
        
        // Call Slack API - fetch extra messages if filtering is enabled (10x to handle heavy bot traffic)
        val fetchLimit = if (args.excludeBotMessages) minOf(args.limit * 10, 1000) else args.limit
        val url = "$SLACK_API_BASE/conversations.history?channel=${args.channelId}&limit=$fetchLimit"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf("Authorization" to "Bearer $botToken")
        )
        
        if (!response.isSuccess) {
            return logAndReturn("❌ HTTP Error: ${response.statusCode}")
        }
        
        // Parse response
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonResponse = json.parseToJsonElement(response.body).jsonObject
            
            val ok = jsonResponse["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            
            if (!ok) {
                val error = jsonResponse["error"]?.jsonPrimitive?.content ?: "Unknown error"
                return logAndReturn("❌ Slack API Error: $error")
            }
            
            val allMessages = jsonResponse["messages"]?.jsonArray?.mapNotNull { msgElement ->
                val msg = msgElement.jsonObject
                val text = msg["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val isBot = msg["bot_id"] != null || msg["subtype"]?.jsonPrimitive?.content == "bot_message"
                
                SlackMessage(
                    user = msg["user"]?.jsonPrimitive?.content ?: "unknown",
                    text = text,
                    timestamp = msg["ts"]?.jsonPrimitive?.content ?: "",
                    threadTs = msg["thread_ts"]?.jsonPrimitive?.contentOrNull
                ) to isBot
            } ?: emptyList()
            
            // Filter messages based on excludeBotMessages flag
            val messages = if (args.excludeBotMessages) {
                allMessages.filter { (msg, isBot) ->
                    !isBot && 
                    !msg.text.startsWith("/cooker") &&
                    !msg.text.contains("Summarizing #") &&
                    !msg.text.contains("Özet oluşturulamadı") &&
                    !msg.text.contains("Cooker AI tarafından") &&
                    !msg.text.contains("Kanal Özeti") &&
                    msg.user != "unknown"  // Filter out bot messages without user ID
                }.map { it.first }.take(args.limit)
            } else {
                allMessages.map { it.first }.take(args.limit)
            }
            
            println("   └─ Total fetched: ${allMessages.size}, After filtering: ${messages.size} (requested: ${args.limit})")
            
            if (messages.isEmpty()) {
                println("   └─ ⚠️ No real user messages found! Channel may only contain bot messages.")
            } else if (messages.size < args.limit) {
                println("   └─ ⚠️ Found fewer messages than requested after filtering bot messages.")
            }
            
            println("   └─ Fetched ${messages.size} messages")
            
            // Log sample messages for debugging
            messages.take(3).forEachIndexed { index, msg ->
                println("   └─ Message ${index + 1}: [${msg.user}] ${msg.text.take(50)}...")
            }
            
            val result = FetchResult(
                channelId = args.channelId,
                messageCount = messages.size,
                messages = messages
            )
            
            val resultJson = json.encodeToString(FetchResult.serializer(), result)
            logAndReturn("✅ Fetched ${messages.size} messages\n$resultJson")
            
        } catch (e: Exception) {
            logAndReturn("❌ Parse error: ${e.message}")
        }
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

/**
 * Tool to list available Slack channels.
 */
object ListSlackChannels : SimpleTool<ListSlackChannels.Args>(
    argsSerializer = Args.serializer(),
    name = "list_slack_channels",
    description = "Lists available Slack channels that the bot has access to. Returns channel names and IDs."
) {

    private const val SLACK_API_BASE = "https://slack.com/api"

    @Serializable
    object Args

    @Serializable
    data class SlackChannel(
        val id: String,
        val name: String,
        val isPrivate: Boolean,
        val memberCount: Int
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] list_slack_channels()")
        
        val botToken = System.getenv("SLACK_BOT_TOKEN")
        
        if (botToken.isNullOrBlank()) {
            return "❌ SLACK_BOT_TOKEN not set"
        }
        
        // Only request public channels by default (private requires groups:read scope)
        val url = "$SLACK_API_BASE/conversations.list?types=public_channel&limit=100"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf("Authorization" to "Bearer $botToken")
        )
        
        if (!response.isSuccess) {
            return "❌ HTTP Error: ${response.statusCode}"
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonResponse = json.parseToJsonElement(response.body).jsonObject
            
            val ok = jsonResponse["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!ok) {
                val error = jsonResponse["error"]?.jsonPrimitive?.content ?: "Unknown error"
                return "❌ Slack API Error: $error"
            }
            
            val channels = jsonResponse["channels"]?.jsonArray?.mapNotNull { ch ->
                val obj = ch.jsonObject
                SlackChannel(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    isPrivate = obj["is_private"]?.jsonPrimitive?.booleanOrNull ?: false,
                    memberCount = obj["num_members"]?.jsonPrimitive?.intOrNull ?: 0
                )
            } ?: emptyList()
            
            println("   └─ Found ${channels.size} channels")
            channels.forEach { ch ->
                println("   └─ #${ch.name} (${ch.id}) - ${ch.memberCount} members")
            }
            
            json.encodeToString(channels)
            
        } catch (e: Exception) {
            "❌ Parse error: ${e.message}"
        }
    }
}

