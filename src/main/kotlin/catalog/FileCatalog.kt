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

    override fun createTable(tableName: String, columns: List<ColumnMeta>) {
        require(tableName.isNotBlank()) { "tableName is blank" }
        if (metas.containsKey(tableName)) error("Table already exists: $tableName")

        val tablePath = baseDir.resolve("$tableName.tbl").toString()

        // TODO : 파일 생성 책임 분리 -> Storage
        val path = Path.of(tablePath)
        if (!Files.exists(path)) {
            Files.createFile(path)
        }

        val meta = TableMeta(
            tableName = tableName,
            filePath = tablePath,
            columns = columns)

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
                val tableName = runCatching { readString(input) }.getOrNull() ?: break
                val filePath = readString(input)
                val columCount = input.readInt()

                val columns = buildList {
                    repeat(columCount) {
                        val name = readString(input)
                        val type = ColumnType.valueOf(readString(input))
                        val nullable = input.readBoolean()

                        add(ColumnMeta(name, type, nullable))
                    }
                }

                metas[tableName] = TableMeta(tableName, filePath, columns)
            }
        }
    }

    private fun appendMeta(meta: TableMeta) {
        // TODO : 루트 경로일 때 parent == null 처리
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
            out.writeInt(meta.columns.size)

            meta.columns.forEach {
                writeString(out, it.name)
                writeString(out, it.type.name) // TODO : enum -> string 변경 시 주의
                out.writeBoolean(it.nullable)
            }
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
