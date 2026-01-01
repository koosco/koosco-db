package com.koosco.db.engine

import com.koosco.sql.CommandResult

/**
 * fileName       : DbEngine
 * author         : koo
 * date           : 2026. 1. 1. 오후 8:40
 * description    :
 */
interface DbEngine {

    fun execute(input: String): CommandResult

    fun close()
}
