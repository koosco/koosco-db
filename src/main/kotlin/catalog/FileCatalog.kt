package com.koosco.catalog

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * fileName       : FileCatalog
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:38
 * description    :
 */
class FileCatalog(
    private val baseDir: Path,
    private val catalogFile: Path,
) : Catalog {

    private val metas = linkedMapOf<String, TableMeta>()

    init {
        Files.createDirectories(baseDir)
        loadIfExists()
    }

    override fun createTable(tableName: String) {
        require(tableName.isNotBlank()) { "tableName is blank" }
        if (metas.containsKey(tableName)) error("Table already exists: $tableName")

        val tablePath = baseDir.resolve("$tableName.tbl").toString()
        val meta = TableMeta(tableName, tablePath)

        val path = Path.of(tablePath)
        if (!Files.exists(path)) {
            Files.createFile(path)
        }

        appendMeta(meta)

        metas[tableName] = meta
    }

    override fun getTableMeta(tableName: String): TableMeta {
        return metas[tableName] ?: error("Table not found: $tableName")
    }

    override fun listTables(): List<String> = metas.keys.toList()

    private fun loadIfExists() {
        if (!Files.exists(catalogFile)) return

        DataInputStream(BufferedInputStream(Files.newInputStream(catalogFile))).use { input ->
            while (true) {
                val name = runCatching { readString(input) }.getOrNull() ?: break
                val path = readString(input)

                metas[name] = TableMeta(name, path)
            }
        }
    }

    private fun appendMeta(meta: TableMeta) {
        Files.createDirectories(catalogFile.parent)
        DataOutputStream(
            BufferedOutputStream(
                Files.newOutputStream(
                    catalogFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            )
        ).use { out ->
            writeString(out, meta.tableName)
            writeString(out, meta.filePath)
        }
    }

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)

        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        require(len >= 0 && len <= 1_000_000) { "Corrupted catalog: invalid string length: $len" }
        val bytes = ByteArray(len)
        input.readFully(bytes)

        return String(bytes, Charsets.UTF_8)
    }
}
