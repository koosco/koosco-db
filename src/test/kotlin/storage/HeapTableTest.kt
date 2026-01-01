package storage

import com.koosco.buffer.BufferPool
import com.koosco.storage.FileDiskManager
import com.koosco.storage.HeapTable
import com.koosco.storage.Page
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * fileName       : HeapTableTest
 * author         : koo
 * date           : 2025. 12. 31. 오전 5:42
 * description    :
 */
class HeapTableTest {

    @Test
    fun givenVariable_whenInsert_thenCanRetrieve() {
        val fileDiskManager = FileDiskManager(Paths.get("data/heap.db"))
        val bufferPool = BufferPool(fileDiskManager, 64, Page.PAGE_SIZE)
        val heap = HeapTable(fileDiskManager, bufferPool)

        val r1 = heap.insertRow("hello".toByteArray())
        val r2 = heap.insertRow("world".toByteArray())
        val r3 = heap.insertRow("koosco-db".toByteArray())

        println(r1)
        println(r2)
        println(r3)

        heap.scanAll().forEach {
            println(String(it))
        }
    }
}
