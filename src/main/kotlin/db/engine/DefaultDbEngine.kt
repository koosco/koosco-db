package db.engine

import com.koosco.buffer.BufferPool
import com.koosco.db.engine.DbEngine
import com.koosco.sql.CommandParser
import com.koosco.sql.CommandResult
import com.koosco.sql.Executor

/**
 * fileName       : DefaultDbEngine
 * author         : koo
 * date           : 2026. 1. 1. 오후 8:41
 * description    :
 */
class DefaultDbEngine(
    private val executor: Executor,
    private val parser: CommandParser,
    private val bufferPool: BufferPool
) : DbEngine {

    override fun execute(input: String): CommandResult {
        val command = parser.parse(input)

        return executor.execute(command)
    }

    /**
     * TODO : flush 시점에 대한 고민 필요
     */
    override fun close() {
        bufferPool.flushAll()
    }
}
