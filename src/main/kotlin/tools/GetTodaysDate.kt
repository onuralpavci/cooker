package com.avci.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

object GetTodaysDate : SimpleTool<GetTodaysDate.Args>(
    argsSerializer = Args.serializer(),
    name = "get_todays_date",
    description = "Gets today's current date and time"
) {

    @Serializable
    object Args

    override suspend fun execute(args: Args): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "Today is ${now.dayOfMonth} ${now.month.name} ${now.year}, ${now.dayOfWeek.name}. Time: ${now.hour}:${now.minute.toString().padStart(2, '0')}"
    }
}
