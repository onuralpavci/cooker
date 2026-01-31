package com.avci.tools.slack

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Tool that posts a message to a Slack channel using the Bot Token.
 * 
 * This is more flexible than webhooks because:
 * - Works with any channel the bot is in
 * - Supports threading (reply to messages)
 * - Supports Block Kit formatting
 * - Single token for all channels
 * 
 * Required Bot Token Scopes:
 * - chat:write
 * - chat:write.public (for channels bot isn't a member of)
 * 
 * Environment variables:
 * - SLACK_BOT_TOKEN: Bot User OAuth Token (xoxb-...)
 */
object PostSlackMessage : SimpleTool<PostSlackMessage.Args>(
    argsSerializer = Args.serializer(),
    name = "post_slack_message",
    description = "Posts a message to a Slack channel. Can post to any channel by ID, supports threading and Block Kit formatting."
) {

    private const val SLACK_API_BASE = "https://slack.com/api"

    @Serializable
    data class Args(
        val channelId: String = "",
        val text: String = "",
        val blocks: JsonArray? = null,
        val threadTs: String? = null,  // Reply in thread to this message
        val replyBroadcast: Boolean = false  // Also send to channel when replying in thread
    )

    @Serializable
    data class PostResult(
        val ok: Boolean,
        val channelId: String,
        val messageTs: String?,
        val error: String?
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] post_slack_message(channel=\"${args.channelId}\")")
        
        val botToken = System.getenv("SLACK_BOT_TOKEN")
        
        if (botToken.isNullOrBlank()) {
            return logAndReturn("❌ SLACK_BOT_TOKEN not set")
        }
        
        if (args.channelId.isBlank()) {
            return logAndReturn("❌ channelId is required")
        }
        
        if (args.text.isBlank() && args.blocks == null) {
            return logAndReturn("❌ Either text or blocks is required")
        }
        
        // Build request body
        val requestBody = buildJsonObject {
            put("channel", args.channelId)
            put("text", args.text)
            
            args.blocks?.let { put("blocks", it) }
            args.threadTs?.let { put("thread_ts", it) }
            
            if (args.replyBroadcast && args.threadTs != null) {
                put("reply_broadcast", true)
            }
        }
        
        println("   └─ Channel: ${args.channelId}")
        println("   └─ Text: ${args.text.take(50)}...")
        args.threadTs?.let { println("   └─ Thread: $it") }
        args.blocks?.let { println("   └─ Blocks: ${it.size} blocks") }
        
        val response = HttpClient.post(
            url = "$SLACK_API_BASE/chat.postMessage",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Bearer $botToken",
                "Content-Type" to "application/json"
            )
        )
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val result = json.parseToJsonElement(response.body).jsonObject
            
            val ok = result["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            
            if (ok) {
                val ts = result["ts"]?.jsonPrimitive?.content
                val channel = result["channel"]?.jsonPrimitive?.content ?: args.channelId
                
                println("   └─ ✅ Message posted! ts=$ts")
                
                val postResult = PostResult(
                    ok = true,
                    channelId = channel,
                    messageTs = ts,
                    error = null
                )
                json.encodeToString(PostResult.serializer(), postResult)
            } else {
                val error = result["error"]?.jsonPrimitive?.content ?: "Unknown error"
                println("   └─ ❌ Error: $error")
                
                // Provide helpful error messages
                val helpText = when (error) {
                    "channel_not_found" -> "Channel not found. Make sure the channel ID is correct."
                    "not_in_channel" -> "Bot is not in the channel. Invite it with /invite @BotName"
                    "missing_scope" -> "Missing scope. Add 'chat:write' to Bot Token Scopes."
                    "invalid_blocks" -> "Invalid Block Kit blocks. Check the format."
                    else -> error
                }
                
                val postResult = PostResult(
                    ok = false,
                    channelId = args.channelId,
                    messageTs = null,
                    error = helpText
                )
                json.encodeToString(PostResult.serializer(), postResult)
            }
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
 * Tool to get channel info (useful for getting channel name from ID)
 */
object GetChannelInfo : SimpleTool<GetChannelInfo.Args>(
    argsSerializer = Args.serializer(),
    name = "get_channel_info",
    description = "Gets information about a Slack channel including name, topic, and member count."
) {

    private const val SLACK_API_BASE = "https://slack.com/api"

    @Serializable
    data class Args(
        val channelId: String = ""
    )

    @Serializable
    data class ChannelInfo(
        val id: String,
        val name: String,
        val topic: String?,
        val purpose: String?,
        val memberCount: Int,
        val isPrivate: Boolean
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] get_channel_info(channel=\"${args.channelId}\")")
        
        val botToken = System.getenv("SLACK_BOT_TOKEN")
        
        if (botToken.isNullOrBlank()) {
            return "❌ SLACK_BOT_TOKEN not set"
        }
        
        if (args.channelId.isBlank()) {
            return "❌ channelId is required"
        }
        
        val response = HttpClient.get(
            url = "$SLACK_API_BASE/conversations.info?channel=${args.channelId}",
            headers = mapOf("Authorization" to "Bearer $botToken")
        )
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val result = json.parseToJsonElement(response.body).jsonObject
            
            val ok = result["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            
            if (ok) {
                val channel = result["channel"]?.jsonObject
                val info = ChannelInfo(
                    id = channel?.get("id")?.jsonPrimitive?.content ?: args.channelId,
                    name = channel?.get("name")?.jsonPrimitive?.content ?: "",
                    topic = channel?.get("topic")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull,
                    purpose = channel?.get("purpose")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull,
                    memberCount = channel?.get("num_members")?.jsonPrimitive?.intOrNull ?: 0,
                    isPrivate = channel?.get("is_private")?.jsonPrimitive?.booleanOrNull ?: false
                )
                
                println("   └─ ✅ #${info.name} (${info.memberCount} members)")
                json.encodeToString(ChannelInfo.serializer(), info)
            } else {
                val error = result["error"]?.jsonPrimitive?.content ?: "Unknown error"
                "❌ Error: $error"
            }
        } catch (e: Exception) {
            "❌ Parse error: ${e.message}"
        }
    }
}

