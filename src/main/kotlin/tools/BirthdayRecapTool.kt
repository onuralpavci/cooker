package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * All-in-one tool that checks birthdays AND fetches GitHub PRs.
 * Designed for smaller LLMs that struggle with multi-step tool chains.
 */
object BirthdayRecapTool : SimpleTool<BirthdayRecapTool.Args>(
    argsSerializer = Args.serializer(),
    name = "get_birthday_recap_data",
    description = "Checks if anyone has a birthday today and fetches their GitHub PRs. Returns all data needed to write a birthday recap. No arguments needed."
) {

    data class TeamMember(val month: Int, val day: Int, val githubUsername: String)
    
    private val teamList = mapOf(
        "Onuralp Avcı" to TeamMember(1, 13, "onuralp-avci_midas"),
        "Ahmet Yılmaz" to TeamMember(3, 15, "ahmet-yilmaz"),
        "Elif Kaya" to TeamMember(7, 22, "elif-kaya-dev"),
        "Mehmet Demir" to TeamMember(1, 13, "mehmet-demir"),
        "Zeynep Çelik" to TeamMember(12, 25, "zeynep-celik"),
        "Can Öztürk" to TeamMember(5, 1, "can-ozturk"),
        "Ayşe Yıldız" to TeamMember(9, 10, "ayse-yildiz"),
        "Burak Şahin" to TeamMember(11, 30, "burak-sahin-dev"),
    )

    @Serializable
    object Args

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] get_birthday_recap_data()")
        
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayMonth = today.monthNumber
        val todayDay = today.dayOfMonth

        // Find birthday people
        val birthdayPeople = teamList.filter { (_, member) ->
            member.month == todayMonth && member.day == todayDay
        }

        if (birthdayPeople.isEmpty()) {
            val result = "No birthdays today (${today.month.name} $todayDay). Wish everyone a great day!"
            println("   └─ Result: $result")
            return result
        }

        // Fetch PRs for each birthday person
        val recapData = StringBuilder()
        recapData.appendLine("🎂 BIRTHDAY RECAP DATA for ${today.month.name} $todayDay")
        recapData.appendLine("=" .repeat(50))

        for ((name, member) in birthdayPeople) {
            recapData.appendLine("\n👤 $name (GitHub: ${member.githubUsername})")
            recapData.appendLine("-".repeat(30))
            
            val prs = fetchGitHubPRs(member.githubUsername)
            recapData.appendLine(prs)
        }

        recapData.appendLine("\n" + "=".repeat(50))
        recapData.appendLine("Now write a warm birthday recap for each person based on their PRs above!")

        val result = recapData.toString()
        println("   └─ Result: Found ${birthdayPeople.size} birthday(s) with PR data")
        return result
    }

    private fun fetchGitHubPRs(username: String): String {
        return try {
            val command = listOf(
                "gh", "search", "prs",
                "--author", username,
                "--merged",
                "--limit", "10",
                "--json", "number,title,repository,createdAt,url"
            )

            println("   └─ Fetching PRs for: $username")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor(30, TimeUnit.SECONDS)
            
            val result = output.toString().trim()
            if (result.isEmpty() || result == "[]") {
                "No merged PRs found recently."
            } else {
                "Recent PRs:\n$result"
            }
        } catch (e: Exception) {
            "Error fetching PRs: ${e.message}"
        }
    }
}

