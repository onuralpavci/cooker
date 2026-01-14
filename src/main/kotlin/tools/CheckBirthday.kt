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

    // Android Team birthday list: name -> (month, day)
    private val birthdayList = mapOf(
        "And Anı Çalık" to Pair(1, 14),         // January 14
        "Arda Ofluoğlu" to Pair(1, 15),         // January 15
        "Aslan Sarı" to Pair(1, 16),            // January 16
        "Cemre Ünal" to Pair(1, 17),            // January 17
        "Doğukan Baş" to Pair(1, 18),           // January 18
        "Eray Özenç" to Pair(1, 19),            // January 19
        "Esra Emirli" to Pair(1, 20),           // January 20
        "Fatih Arslan" to Pair(1, 21),          // January 21
        "Mehmet Altıparmak" to Pair(1, 22),     // January 22
        "Mehmet Kaya" to Pair(1, 23),           // January 23
        "Mustafa Sevgi" to Pair(1, 24),         // January 24
        "Onur Vatansever" to Pair(1, 25),       // January 25
        "Onuralp Avcı" to Pair(1, 26)           // January 26
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
