package com.avci.tools.github

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

/**
 * Tool that maps a person's name to their GitHub username.
 * 
 * TODO: In the future, this could be fetched from a config file or API.
 */
object GetGitHubUsername : SimpleTool<GetGitHubUsername.Args>(
    argsSerializer = Args.serializer(),
    name = "get_github_username",
    description = "Gets the GitHub username for a given person's name. Provide the person's full name to get their GitHub username."
) {

    // Android Team mapping: name -> GitHub username
    // TODO: Move this to a config file
    private val usernameMap = mapOf(
        "and anı çalık" to "ani-calik_midas",
        "arda ofluoğlu" to "arda-ofluoglu_midas",
        "aslan sarı" to "aslan-sari_midas",
        "cemre ünal" to "cemre-unal_midas",
        "doğukan baş" to "dogukan-bas_midas",
        "eray özenç" to "eray-ozenc_midas",
        "esra emirli" to "esra-emirli_midas",
        "fatih arslan" to "fatih-arslan_midas",
        "mehmet altıparmak" to "mehmet-altiparmak_midas",
        "mehmet kaya" to "mehmet-kaya_midas",
        "mustafa sevgi" to "mustafa-sevgi_midas",
        "onur vatansever" to "onur-vatansever_midas",
        "onuralp avcı" to "onuralp-avci_midas"
    )

    @Serializable
    data class Args(
        val name: String = ""
    )

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] get_github_username(name=\"${args.name}\")")
        
        if (args.name.isBlank()) {
            return logAndReturn("❌ Error: name is required")
        }
        
        val normalizedName = args.name.lowercase().trim()
        
        val username = usernameMap[normalizedName]
            ?: usernameMap.entries.find { (key, _) -> 
                key.contains(normalizedName) || normalizedName.contains(key)
            }?.value

        return if (username != null) {
            logAndReturn("✅ GitHub username for '${args.name}': $username")
        } else {
            logAndReturn("❌ Not found. Available: ${usernameMap.keys.joinToString(", ")}")
        }
    }
    
    private fun logAndReturn(message: String): String {
        println("   └─ $message")
        return message
    }
}

