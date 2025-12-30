package com.koosco.sql

/**
 * fileName       : CommandParser
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:16
 * description    : test용 커맨드 파서
 */
class CommandParser {

    fun parse(input: String): Command {
        val tokens = input.trim().split(" ", limit = 2)

        return when (tokens[0].lowercase()) {
            "insert" -> {
                require(tokens.size == 2) { "Insert command requires value." }
                InsertCommand(tokens[1])
            }
            "select" -> SelectCommand
            else -> error("Unknown command: ${tokens[0]}")
        }
    }
}
