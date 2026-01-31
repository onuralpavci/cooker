package com.avci.core.utils

import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP client utilities for API calls.
 * 
 * This is a lightweight HTTP client that doesn't require additional dependencies.
 * For more complex use cases, consider using Ktor or OkHttp.
 */
object HttpClient {
    
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    
    /**
     * Performs a GET request and returns the response body.
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30000
    ): HttpResponse {
        println("🌐 [HTTP] GET $url")
        headers.forEach { (k, v) -> 
            val maskedValue = if (k.lowercase().contains("auth") || k.lowercase().contains("token")) 
                "${v.take(10)}..." else v
            println("   └─ Header: $k = $maskedValue")
        }
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            val responseCode = connection.responseCode
            val responseBody = readResponse(connection)
            
            println("   └─ Response: $responseCode (${responseBody.length} chars)")
            
            HttpResponse(
                statusCode = responseCode,
                body = responseBody,
                isSuccess = responseCode in 200..299
            )
        } catch (e: Exception) {
            println("   └─ Error: ${e.message}")
            HttpResponse(
                statusCode = -1,
                body = "Error: ${e.message}",
                isSuccess = false
            )
        }
    }
    
    /**
     * Performs a POST request with JSON body.
     */
    fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30000
    ): HttpResponse {
        println("🌐 [HTTP] POST $url")
        println("   └─ Body: ${body.take(100)}${if (body.length > 100) "..." else ""}")
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            // Write body
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(body.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            
            val responseCode = connection.responseCode
            val responseBody = readResponse(connection)
            
            println("   └─ Response: $responseCode (${responseBody.length} chars)")
            
            HttpResponse(
                statusCode = responseCode,
                body = responseBody,
                isSuccess = responseCode in 200..299
            )
        } catch (e: Exception) {
            println("   └─ Error: ${e.message}")
            HttpResponse(
                statusCode = -1,
                body = "Error: ${e.message}",
                isSuccess = false
            )
        }
    }
    
    private fun readResponse(connection: HttpURLConnection): String {
        val inputStream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        
        return BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
    }
}

/**
 * HTTP response wrapper
 */
data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val isSuccess: Boolean
) {
    inline fun <reified T> parseJson(): T? {
        return try {
            HttpClient.json.decodeFromString<T>(body)
        } catch (e: Exception) {
            println("   └─ JSON parse error: ${e.message}")
            null
        }
    }
}

/**
 * Command runner utility for CLI tools (gh, curl, etc.)
 */
object CommandRunner {
    
    fun run(
        command: List<String>,
        timeoutSeconds: Long = 60,
        workingDir: String? = null,
        ignoreErrors: Boolean = false
    ): CommandResult {
        val cmdString = command.joinToString(" ")
        println("⚡ [CMD] $cmdString")
        
        return try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            workingDir?.let { processBuilder.directory(java.io.File(it)) }
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                println("   └─ Timeout after ${timeoutSeconds}s")
                return CommandResult(
                    exitCode = -1,
                    output = "Command timed out after ${timeoutSeconds} seconds",
                    isSuccess = false
                )
            }
            
            val exitCode = process.exitValue()
            val outputStr = output.toString().trim()
            
            if (exitCode != 0 && !ignoreErrors) {
                println("   └─ Exit code: $exitCode")
                println("   └─ Output: ${outputStr.take(200)}")
            } else {
                println("   └─ Success (${outputStr.length} chars)")
            }
            
            CommandResult(
                exitCode = exitCode,
                output = outputStr,
                isSuccess = exitCode == 0 || ignoreErrors
            )
        } catch (e: Exception) {
            println("   └─ Exception: ${e.message}")
            CommandResult(
                exitCode = -1,
                output = "Error: ${e.message}",
                isSuccess = false
            )
        }
    }
}

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val isSuccess: Boolean
)

