package com.koosco.sql

/**
 * fileName       : CommandParser
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:16
 * description    : test용 커맨드 파서
 */
class CommandParser {

    fun parse(input: String): Command {

        val s = input.trim()
        if (s.isEmpty()) error("Empty command")

        val tokens = s.split(Regex("\\s+"))

        return when (tokens[0].lowercase()) {
            "create" -> {
                require(tokens.size >= 3) { "Usage: create table <table_name>" }
                require(
                    "table".equals(
                        tokens[1],
                        ignoreCase = true
                    )
                ) { "Usage: create table <table_name>" }

                val tableName = tokens[2]
                val columnsRaw = tokens.drop(3).joinToString(" ")
                    .removePrefix("(")
                    .removeSuffix(")")

                val columns = columnsRaw.split(",").map {
                    val p = it.trim().split(" ")
                    CreateColumnDef(name = p[0], type = p[1], nullable = !p.contains("notnull"))
                }

                CreateTableCommand(tokens[2], columns)
            }

            "insert" -> {
                require(tokens.size >= 4) { "Usage: insert into <table> <value...>" }
                require(
                    "into".equals(
                        tokens[1],
                        ignoreCase = true
                    )
                ) { "Usage: insert into <table> <value...>" }

                val table = tokens[2]
                val value = tokens.drop(3).joinToString(" ")

                InsertCommand(table, value)
            }

            "select" -> {
                val fromIdx = tokens.indexOfFirst { "from".equals(it, ignoreCase = true) }
                require(fromIdx != -1 && fromIdx + 1 < tokens.size) { "Usage: select * from <table>" }
                SelectCommand(tokens[fromIdx + 1])
            }

            "show" -> {
                require(
                    tokens.size == 2 && "tables".equals(
                        tokens[1],
                        ignoreCase = true
                    )
                ) { "Usage: show tables" }
                ShowTablesCommand
            }

            "describe" -> {
                require(tokens.size == 2) { "Usage: describe <table>" }
                DescribeTableCommand(tokens[1])
            }

            else -> error("Unknown command: ${tokens[0]}")
        }
    }
}
