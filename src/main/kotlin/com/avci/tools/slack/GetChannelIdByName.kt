package com.avci.tools.slack

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tool to get Slack channel ID by channel name.
 * Useful when agents need to work with channels by name instead of ID.
 */
object GetChannelIdByName : SimpleTool<GetChannelIdByName.Args>(
    argsSerializer = Args.serializer(),
    name = "get_channel_id_by_name",
    description = "Get Slack channel ID from channel name. Returns the channel ID if found."
) {
    
    private val jsonParser = Json { ignoreUnknownKeys = true }
    
    @Serializable
    data class Args(
        val channelName: String = "",
        val includePrivate: Boolean = false
    )
    
    @Serializable
    private data class SlackResponse(
        val ok: Boolean,
        val channels: List<SlackChannel> = emptyList(),
        val error: String? = null
    )
    
    @Serializable
    private data class SlackChannel(
        val id: String,
        val name: String
    )
    
    override suspend fun execute(args: Args): String {
        val token = System.getenv("SLACK_BOT_TOKEN")
            ?: return """{"error": "SLACK_BOT_TOKEN not set"}"""
        
        val searchName = args.channelName.removePrefix("#").lowercase().trim()
        println("🔧 [TOOL] get_channel_id_by_name(channelName=\"${args.channelName}\")")
        println("   └─ Searching for: $searchName")
        
        // Determine channel types to search
        val types = if (args.includePrivate) "public_channel,private_channel" else "public_channel"
        
        val url = URL("https://slack.com/api/conversations.list?types=$types&limit=1000")
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val response = connection.inputStream.bufferedReader().readText()
            val slackResponse = jsonParser.decodeFromString<SlackResponse>(response)
            
            if (!slackResponse.ok) {
                println("   └─ ❌ Slack API Error: ${slackResponse.error}")
                return """{"error": "${slackResponse.error}", "found": false}"""
            }
            
            // Find channel by name
            val foundChannel = slackResponse.channels.find { 
                it.name.lowercase() == searchName 
            }
            
            if (foundChannel != null) {
                println("   └─ ✅ Found: #${foundChannel.name} → ${foundChannel.id}")
                """{"channelId": "${foundChannel.id}", "channelName": "${foundChannel.name}", "found": true}"""
            } else {
                // List available channels for suggestion
                val availableChannels = slackResponse.channels
                    .take(10)
                    .joinToString(", ") { "#${it.name}" }
                
                println("   └─ ❌ Channel not found: #${args.channelName}")
                println("   └─ Available channels: $availableChannels")
                """{"error": "Channel not found", "searchedName": "${args.channelName}", "availableChannels": "$availableChannels", "found": false}"""
            }
            
        } catch (e: Exception) {
            println("   └─ ❌ Error: ${e.message}")
            """{"error": "${e.message}"}"""
        } finally {
            connection.disconnect()
        }
    }
}
