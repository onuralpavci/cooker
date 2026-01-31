package com.avci.tools.figma

import ai.koog.agents.core.tools.SimpleTool
import com.avci.core.utils.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Figma API Tools for design data extraction.
 * 
 * Uses Figma REST API.
 * 
 * Environment variables:
 * - FIGMA_ACCESS_TOKEN: Personal access token from figma.com/developers
 * 
 * Note: For full design context, consider using the Figma MCP server
 * which provides richer code generation capabilities.
 */
object GetFigmaDesign : SimpleTool<GetFigmaDesign.Args>(
    argsSerializer = Args.serializer(),
    name = "get_figma_design",
    description = "Fetches design information from a Figma file URL. Extracts component name, colors, typography, and layout details."
) {

    private const val FIGMA_API_BASE = "https://api.figma.com/v1"

    @Serializable
    data class Args(
        val figmaUrl: String = "",
        val nodeId: String = ""
    )

    @Serializable
    data class FigmaDesignInfo(
        val fileKey: String,
        val nodeId: String,
        val nodeName: String,
        val nodeType: String,
        val width: Float?,
        val height: Float?,
        val colors: List<ColorInfo>,
        val typography: List<TypographyInfo>,
        val children: List<String>
    )

    @Serializable
    data class ColorInfo(
        val name: String,
        val hex: String,
        val opacity: Float
    )

    @Serializable
    data class TypographyInfo(
        val fontFamily: String,
        val fontSize: Float,
        val fontWeight: Int,
        val lineHeight: Float?
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] get_figma_design(url=\"${args.figmaUrl.take(50)}...\")")
        
        val accessToken = System.getenv("FIGMA_ACCESS_TOKEN")
        
        if (accessToken.isNullOrBlank()) {
            return logAndReturn("""
                ❌ FIGMA_ACCESS_TOKEN not set.
                Get it from: figma.com → Settings → Personal access tokens
                
                💡 Tip: For richer design context, use the Figma MCP server:
                The mcp_Figma_get_design_context tool provides better code generation.
            """.trimIndent())
        }
        
        if (args.figmaUrl.isBlank()) {
            return logAndReturn("❌ figmaUrl is required")
        }
        
        // Parse Figma URL
        // Format: https://www.figma.com/design/FILE_KEY/FILE_NAME?node-id=NODE_ID
        val fileKeyRegex = Regex("""figma\.com/(?:design|file)/([a-zA-Z0-9]+)""")
        val nodeIdRegex = Regex("""node-id=([0-9]+-[0-9]+)""")
        
        val fileKey = fileKeyRegex.find(args.figmaUrl)?.groupValues?.get(1)
            ?: return logAndReturn("❌ Could not extract file key from URL")
        
        val nodeId = args.nodeId.ifBlank {
            nodeIdRegex.find(args.figmaUrl)?.groupValues?.get(1)?.replace("-", ":")
        } ?: return logAndReturn("❌ Could not extract node ID from URL. Provide nodeId parameter.")
        
        println("   └─ File Key: $fileKey")
        println("   └─ Node ID: $nodeId")
        
        // Fetch file data
        val url = "$FIGMA_API_BASE/files/$fileKey/nodes?ids=$nodeId"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf("X-Figma-Token" to accessToken)
        )
        
        if (!response.isSuccess) {
            return logAndReturn("❌ HTTP ${response.statusCode}: ${response.body.take(200)}")
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val data = json.parseToJsonElement(response.body).jsonObject
            
            val nodes = data["nodes"]?.jsonObject ?: return logAndReturn("❌ No nodes in response")
            val nodeData = nodes[nodeId]?.jsonObject?.get("document")?.jsonObject
                ?: return logAndReturn("❌ Node $nodeId not found")
            
            val designInfo = parseNodeInfo(fileKey, nodeId, nodeData)
            
            println("   └─ ✅ Found: ${designInfo.nodeName} (${designInfo.nodeType})")
            println("   └─ Size: ${designInfo.width} x ${designInfo.height}")
            println("   └─ Colors: ${designInfo.colors.size}")
            println("   └─ Typography: ${designInfo.typography.size}")
            
            json.encodeToString(FigmaDesignInfo.serializer(), designInfo)
            
        } catch (e: Exception) {
            logAndReturn("❌ Parse error: ${e.message}")
        }
    }
    
    private fun parseNodeInfo(fileKey: String, nodeId: String, node: JsonObject): FigmaDesignInfo {
        val colors = mutableListOf<ColorInfo>()
        val typography = mutableListOf<TypographyInfo>()
        val children = mutableListOf<String>()
        
        // Extract colors from fills
        node["fills"]?.jsonArray?.forEach { fill ->
            val fillObj = fill.jsonObject
            if (fillObj["type"]?.jsonPrimitive?.content == "SOLID") {
                val color = fillObj["color"]?.jsonObject
                val r = ((color?.get("r")?.jsonPrimitive?.floatOrNull ?: 0f) * 255).toInt()
                val g = ((color?.get("g")?.jsonPrimitive?.floatOrNull ?: 0f) * 255).toInt()
                val b = ((color?.get("b")?.jsonPrimitive?.floatOrNull ?: 0f) * 255).toInt()
                val hex = String.format("#%02X%02X%02X", r, g, b)
                colors.add(ColorInfo(
                    name = "fill",
                    hex = hex,
                    opacity = fillObj["opacity"]?.jsonPrimitive?.floatOrNull ?: 1f
                ))
            }
        }
        
        // Extract typography from style
        node["style"]?.jsonObject?.let { style ->
            typography.add(TypographyInfo(
                fontFamily = style["fontFamily"]?.jsonPrimitive?.content ?: "Unknown",
                fontSize = style["fontSize"]?.jsonPrimitive?.floatOrNull ?: 16f,
                fontWeight = style["fontWeight"]?.jsonPrimitive?.intOrNull ?: 400,
                lineHeight = style["lineHeightPx"]?.jsonPrimitive?.floatOrNull
            ))
        }
        
        // Get children names
        node["children"]?.jsonArray?.forEach { child ->
            val childName = child.jsonObject["name"]?.jsonPrimitive?.content
            childName?.let { children.add(it) }
        }
        
        val bbox = node["absoluteBoundingBox"]?.jsonObject
        
        return FigmaDesignInfo(
            fileKey = fileKey,
            nodeId = nodeId,
            nodeName = node["name"]?.jsonPrimitive?.content ?: "Unknown",
            nodeType = node["type"]?.jsonPrimitive?.content ?: "Unknown",
            width = bbox?.get("width")?.jsonPrimitive?.floatOrNull,
            height = bbox?.get("height")?.jsonPrimitive?.floatOrNull,
            colors = colors,
            typography = typography,
            children = children
        )
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

/**
 * Tool to extract variables/design tokens from a Figma file.
 */
object GetFigmaVariables : SimpleTool<GetFigmaVariables.Args>(
    argsSerializer = Args.serializer(),
    name = "get_figma_variables",
    description = "Fetches design tokens/variables from a Figma file. Returns color tokens, spacing, and typography scales."
) {

    private const val FIGMA_API_BASE = "https://api.figma.com/v1"

    @Serializable
    data class Args(
        val fileKey: String = ""
    )

    @Serializable
    data class VariableCollection(
        val name: String,
        val variables: List<Variable>
    )

    @Serializable
    data class Variable(
        val name: String,
        val type: String,
        val value: String
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] get_figma_variables(fileKey=\"${args.fileKey}\")")
        
        val accessToken = System.getenv("FIGMA_ACCESS_TOKEN")
        
        if (accessToken.isNullOrBlank()) {
            return "❌ FIGMA_ACCESS_TOKEN not set"
        }
        
        if (args.fileKey.isBlank()) {
            return "❌ fileKey is required"
        }
        
        val url = "$FIGMA_API_BASE/files/${args.fileKey}/variables/local"
        
        val response = HttpClient.get(
            url = url,
            headers = mapOf("X-Figma-Token" to accessToken)
        )
        
        if (!response.isSuccess) {
            return "❌ HTTP ${response.statusCode}: ${response.body.take(200)}"
        }
        
        // TODO: Parse variables response
        // The Figma Variables API response structure is complex
        // For now, return raw response for debugging
        
        println("   └─ ✅ Variables fetched (${response.body.length} chars)")
        return response.body
    }
}

