package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Tool that checks if today is anyone's birthday from the team list.
 * Returns the list of people who have birthdays today.
 */
object CheckBirthday : SimpleTool<CheckBirthday.Args>(
    argsSerializer = Args.serializer(),
    name = "check_birthday",
    description = "Checks if today is anyone's birthday from the team. Returns who has a birthday today. No arguments needed."
) {

    // Dummy birthday list: name -> (month, day)
    private val birthdayList = mapOf(
        "Onuralp Avcı" to Pair(1, 13),      // January 13
        "Ahmet Yılmaz" to Pair(3, 15),      // March 15
        "Elif Kaya" to Pair(7, 22),         // July 22
        "Mehmet Demir" to Pair(1, 13),      // January 13 (same as Onuralp for testing)
        "Zeynep Çelik" to Pair(12, 25),     // December 25
        "Can Öztürk" to Pair(5, 1),         // May 1
        "Ayşe Yıldız" to Pair(9, 10),       // September 10
        "Burak Şahin" to Pair(11, 30),      // November 30
    )

    @Serializable
    object Args  // No arguments needed

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL CALL] check_birthday()")
        
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayMonth = today.monthNumber
        val todayDay = today.dayOfMonth

        val birthdayPeople = birthdayList.filter { (_, date) ->
            date.first == todayMonth && date.second == todayDay
        }.keys.toList()

        val result = if (birthdayPeople.isNotEmpty()) {
            val names = birthdayPeople.joinToString(", ")
            "YES! Today (${today.month.name} $todayDay) is a birthday! Birthday celebrants: $names"
        } else {
            "No birthdays today (${today.month.name} $todayDay). The team birthday list has ${birthdayList.size} people registered."
        }
        
        println("   └─ Result: $result")
        return result
    }
}
