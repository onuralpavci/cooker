package com.avci.tools.slack

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Tool that sends a Slack Block Kit notification to a webhook URL.
 * 
 * Environment variables:
 * - SLACK_UI_TEST_WEBHOOK_URL: Webhook URL for notifications
 */
object SendSlackNotification : SimpleTool<SendSlackNotification.Args>(
    argsSerializer = Args.serializer(),
    name = "send_slack_notification",
    description = "Sends a Slack notification using Block Kit. Provide 'text' (fallback) and 'blocks' (Block Kit array)."
) {

    @Serializable
    data class Args(
        val text: String,
        val blocks: JsonArray
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] send_slack_notification()")
        println("   └─ Text: ${args.text.take(50)}...")
        println("   └─ Blocks: ${args.blocks.size} blocks")
        
        val webhookUrl = System.getenv("SLACK_UI_TEST_WEBHOOK_URL")
        
        if (webhookUrl.isNullOrBlank()) {
            return logAndReturn("❌ SLACK_UI_TEST_WEBHOOK_URL not set")
        }
        
        // Build payload
        val payload = buildMap<String, JsonElement> {
            put("text", JsonPrimitive(args.text))
            put("blocks", args.blocks)
        }
        val jsonPayload = HttpClient.json.encodeToString(payload)
        
        println("   └─ Payload size: ${jsonPayload.length} chars")
        
        val response = HttpClient.post(webhookUrl, jsonPayload)
        
        return if (response.isSuccess) {
            logAndReturn("✅ Slack notification sent successfully!")
        } else {
            logAndReturn("❌ Failed: HTTP ${response.statusCode} - ${response.body}")
        }
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

