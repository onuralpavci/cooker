package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tool that sends a Slack Block Kit notification to a configured webhook URL.
 */
object SendSlackNotification : SimpleTool<SendSlackNotification.Args>(
    argsSerializer = Args.serializer(),
    name = "send_slack_notification",
    description = "Sends a Slack notification with Block Kit format. Provide the complete Block Kit JSON payload as a string."
) {

    @Serializable
    data class Args(
        val blockKitJson: String
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] send_slack_notification()")
        
        val webhookUrl = System.getenv("SLACK_UI_TEST_WEBHOOK_URL")
        
        if (webhookUrl.isNullOrBlank()) {
            val error = "Error: SLACK_UI_TEST_WEBHOOK_URL environment variable is not set"
            println("   └─ $error")
            return error
        }
        
        if (args.blockKitJson.isBlank()) {
            val error = "Error: blockKitJson is required"
            println("   └─ $error")
            return error
        }
        
        return try {
            println("   └─ Webhook URL: ${webhookUrl.take(50)}...")
            println("   └─ JSON payload size: ${args.blockKitJson.length} chars")
            
            val url = URL(webhookUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Write JSON payload
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(args.blockKitJson.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            
            // Get response
            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            
            val result = if (responseCode in 200..299) {
                "✅ Slack notification sent successfully! Response: $response"
            } else {
                "❌ Failed to send Slack notification. HTTP $responseCode: $response"
            }
            
            println("   └─ Result: $result")
            result
            
        } catch (e: Exception) {
            val error = "Error sending Slack notification: ${e.message}"
            println("   └─ $error")
            error
        }
    }
}

