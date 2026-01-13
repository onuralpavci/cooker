package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

/**
 * Tool that maps a person's name to their GitHub username.
 */
object GetGitHubUsername : SimpleTool<GetGitHubUsername.Args>(
    argsSerializer = Args.serializer(),
    name = "get_github_username",
    description = "Gets the GitHub username for a given person's name. Provide the person's full name to get their GitHub username."
) {

    // Dummy mapping: name -> GitHub username
    private val usernameMap = mapOf(
        "onuralp avcı" to "onuralp-avci_midas",
        "ahmet yılmaz" to "ahmet-yilmaz",
        "elif kaya" to "elif-kaya-dev",
        "mehmet demir" to "mehmet-demir",
        "zeynep çelik" to "zeynep-celik",
        "can öztürk" to "can-ozturk",
        "ayşe yıldız" to "ayse-yildiz",
        "burak şahin" to "burak-sahin-dev",
    )

    @Serializable
    data class Args(
        val name: String = ""
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] get_github_username(name=\"${args.name}\")")
        
        if (args.name.isBlank()) {
            val result = "Error: name is required. Please provide a person's name to get their GitHub username."
            println("   └─ Result: $result")
            return result
        }
        
        val normalizedName = args.name.lowercase().trim()
        
        val username = usernameMap[normalizedName]
            ?: usernameMap.entries.find { (key, _) -> 
                key.contains(normalizedName) || normalizedName.contains(key)
            }?.value

        val result = if (username != null) {
            "GitHub username for '${args.name}' is: $username"
        } else {
            "Could not find GitHub username for '${args.name}'. Available team members: ${usernameMap.keys.joinToString(", ")}"
        }
        
        println("   └─ Result: $result")
        return result
    }
}
