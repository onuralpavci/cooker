package com.avci.tools.common

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Tool that gets today's date and time.
 */
object GetTodaysDate : SimpleTool<GetTodaysDate.Args>(
    argsSerializer = Args.serializer(),
    name = "get_todays_date",
    description = "Gets today's current date and time in Turkey timezone."
) {

    @Serializable
    object Args

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] get_todays_date()")
        
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val result = "Today is ${now.dayOfMonth} ${now.month.name} ${now.year}, ${now.dayOfWeek.name}. Time: ${now.hour}:${now.minute.toString().padStart(2, '0')}"
        
        println("   └─ $result")
        return result
    }
}

/**
 * Tool that checks if today is anyone's birthday from the team list.
 */
object CheckBirthday : SimpleTool<CheckBirthday.Args>(
    argsSerializer = Args.serializer(),
    name = "check_birthday",
    description = "Checks if today is anyone's birthday from the Android team. Returns who has a birthday today."
) {

    // Android Team birthday list: name -> (month, day)
    // TODO: Move to config file
    private val birthdayList = mapOf(
        "And Anı Çalık" to Pair(1, 14),
        "Arda Ofluoğlu" to Pair(1, 15),
        "Aslan Sarı" to Pair(1, 16),
        "Cemre Ünal" to Pair(1, 15),
        "Doğukan Baş" to Pair(1, 18),
        "Eray Özenç" to Pair(1, 19),
        "Esra Emirli" to Pair(1, 20),
        "Fatih Arslan" to Pair(1, 21),
        "Mehmet Altıparmak" to Pair(1, 22),
        "Mehmet Kaya" to Pair(1, 23),
        "Mustafa Sevgi" to Pair(1, 24),
        "Onur Vatansever" to Pair(1, 25),
        "Onuralp Avcı" to Pair(1, 26)
    )

    @Serializable
    object Args

    override suspend fun execute(args: Args): String {
        println("🔧 [TOOL] check_birthday()")
        
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayMonth = today.monthNumber
        val todayDay = today.dayOfMonth

        val birthdayPeople = birthdayList.filter { (_, date) ->
            date.first == todayMonth && date.second == todayDay
        }.keys.toList()

        val result = if (birthdayPeople.isNotEmpty()) {
            val names = birthdayPeople.joinToString(", ")
            "🎂 YES! Today (${today.month.name} $todayDay) is a birthday! Celebrants: $names"
        } else {
            "No birthdays today (${today.month.name} $todayDay). Team has ${birthdayList.size} members registered."
        }
        
        println("   └─ $result")
        return result
    }
}

