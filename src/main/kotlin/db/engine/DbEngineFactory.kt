package com.koosco.db.engine

import com.koosco.buffer.BufferPool
import com.koosco.catalog.FileCatalog
import com.koosco.sql.CommandParser
import com.koosco.sql.Executor
import com.koosco.storage.FileDiskManager
import com.koosco.storage.Page
import com.koosco.storage.TableManager
import db.engine.DefaultDbEngine
import java.nio.file.Path

/**
 * fileName       : DbEngineFactory
 * author         : koo
 * date           : 2026. 1. 1. 오후 9:08
 * description    :
 */
object DbEngineFactory {

    fun create(dbPath: Path): DbEngine {
        val baseDir = dbPath
        val catalogFile = baseDir.resolve("catalog.meta")
        val catalog = FileCatalog(
            baseDir = baseDir,
            catalogFile = catalogFile
        )

        val dbFile = baseDir.resolve("koosco.db")
        val diskManager = FileDiskManager(dbFile)

        val bufferPool = BufferPool(
            diskManager = diskManager,
            poolSize = 64,
            pageSize = Page.PAGE_SIZE
        )

        val tableManager = TableManager(
            catalog = catalog,
            diskManager = diskManager,
            bufferPool = bufferPool
        )

        val executor = Executor(
            catalog = catalog,
            tableManager = tableManager
        )

        val parser = CommandParser()

        return DefaultDbEngine(executor, parser, bufferPool)
    }
}
