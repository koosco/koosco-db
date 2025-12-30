package com.koosco.sql

/**
 * fileName       : Command
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:15
 * description    :
 */
sealed interface Command

data class InsertCommand(val value: String): Command

object SelectCommand: Command
