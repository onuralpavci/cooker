package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Tool that checks if today is anyone's birthday from the team list.
 * Returns the list of people who have birthdays today WITH their GitHub usernames.
 */
object CheckBirthday : SimpleTool<CheckBirthday.Args>(
    argsSerializer = Args.serializer(),
    name = "check_birthday",
    description = "Checks if today is anyone's birthday from the team. Returns who has a birthday today WITH their GitHub username. No arguments needed."
) {

    // Team data: name -> (month, day, githubUsername)
    data class TeamMember(val month: Int, val day: Int, val githubUsername: String)
    
    private val teamList = mapOf(
        "Onuralp Avcı" to TeamMember(1, 13, "onuralp-avci_midas"),
        "Ahmet Yılmaz" to TeamMember(3, 15, "ahmet-yilmaz"),
        "Elif Kaya" to TeamMember(7, 22, "elif-kaya-dev"),
        "Mehmet Demir" to TeamMember(1, 13, "mehmet-demir"),  // Same day as Onuralp
        "Zeynep Çelik" to TeamMember(12, 25, "zeynep-celik"),
        "Can Öztürk" to TeamMember(5, 1, "can-ozturk"),
        "Ayşe Yıldız" to TeamMember(9, 10, "ayse-yildiz"),
        "Burak Şahin" to TeamMember(11, 30, "burak-sahin-dev"),
    )

    @Serializable
    object Args  // No arguments needed

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] check_birthday()")
        
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayMonth = today.monthNumber
        val todayDay = today.dayOfMonth

        val birthdayPeople = teamList.filter { (_, member) ->
            member.month == todayMonth && member.day == todayDay
        }

        val result = if (birthdayPeople.isNotEmpty()) {
            val peopleInfo = birthdayPeople.map { (name, member) ->
                "- $name (GitHub: ${member.githubUsername})"
            }.joinToString("\n")
            
            """YES! Today (${today.month.name} $todayDay) is a birthday!
            |
            |Birthday celebrants with their GitHub usernames:
            |$peopleInfo
            |
            |Use the fetch_github_prs tool with these EXACT GitHub usernames to get their PRs.""".trimMargin()
        } else {
            "No birthdays today (${today.month.name} $todayDay). The team has ${teamList.size} members registered."
        }
        
        println("   └─ Result: $result")
        return result
    }
}
