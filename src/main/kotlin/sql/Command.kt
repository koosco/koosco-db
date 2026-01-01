package com.koosco.sql

/**
 * fileName       : Command
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:15
 * description    :
 */
sealed interface Command

data class CreateTableCommand(val tableName: String, val columns: List<CreateColumnDef>): Command

data class CreateColumnDef(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

object ShowTablesCommand: Command

data class InsertCommand(val tableName: String, val value: String): Command

data class SelectCommand(val tableName: String): Command

data class DescribeTableCommand(val tableName: String): Command
