package com.koosco.buffer

import com.koosco.common.PageId
import com.koosco.storage.DiskManager
import java.nio.ByteBuffer

/**
 * fileName       : BufferPool
 * author         : koo
 * date           : 2026. 1. 1. 오후 7:41
 * description    : Minimal Buffer Pool
 * - eviction 없음
 * - pin/unpin 없음
 * - dirty tracking + flushAll 지원
 *
 * 목표 : diskManager 직접 호출을 제거하고, BufferPool를 통해 페이지를 읽고 쓰도록 유도
 */
class BufferPool(
    private val diskManager: DiskManager,
    private val poolSize: Int,
    private val pageSize: Int
) {

    private val frames: Array<Frame> = Array(poolSize) { Frame.empty(pageSize)}
    private val pageTable: MutableMap<PageId, Int> = HashMap()
    private var nextFreeFrame: Int = 0

    /**
     * pageId에 해당하는 페이지 버퍼 반환
     * - cache hit : @return memory frame
     * - cache miss : disk에서 읽어서 메모리 할당 후 반환
     */
    fun getPage(pageId: PageId): ByteBuffer {
        val hit = pageTable[pageId]
        if (hit != null) {
            return frames[hit].buffer.duplicate()
        }

        val frameId = allocateFrameOrThrow()
        val frame = frames[frameId]

        frame.bind(pageId)
        diskManager.readPage(pageId, frame.buffer)

        pageTable[pageId] = frameId
        return frame.buffer.duplicate()
    }

    /**
     * caller가 페이지를 수정했음을 표시
     * TODO : pin/unpin 기능 추가 시점에 재고
     */
    fun markDirty(pageId: PageId) {
        val frameId = pageTable[pageId]
            ?: throw IllegalStateException("markDirty: page not in buffer: $pageId")
        frames[frameId].dirty = true
    }

    /**
     * 특정 페이지만 disk flush
     */
    fun flushPage(pageId: PageId) {
        val frameId = pageTable[pageId] ?: return
        val frame = frames[frameId]

        if (frame.pageId != pageId) return

        if (frame.dirty) {
            diskManager.writePage(pageId, frame.buffer)
            frame.dirty = false
        }
    }

    /**
     * 버퍼 풀의 모든 더티 페이지를 disk에 flush
     */
    fun flushAll() {
        for (frame in frames) {
            val pageId = frame.pageId ?: continue
            if (frame.dirty) {
                diskManager.writePage(pageId, frame.buffer)
                frame.dirty = false
            }
        }
    }

    private fun allocateFrameOrThrow(): Int {
        if (nextFreeFrame >= poolSize) {
            throw IllegalStateException("BufferPool is full (poolSize=$poolSize). Implement eviction later or increase pool size.")
        }
        return nextFreeFrame++
    }

    /**
     * For debug purpose
     */
    fun loadedPages(): List<PageId> = frames.mapNotNull { it.pageId }
}

data class Frame(
    var pageId: PageId?,
    val buffer: ByteBuffer,
    var dirty: Boolean
) {

    fun bind(newPageId: PageId) {
        pageId = newPageId
        dirty = false
        buffer.clear()
    }

    companion object {
        fun empty(pageSize: Int): Frame = Frame(
            pageId = null,
            buffer = ByteBuffer.allocate(pageSize),
            dirty = false
        )
    }
}
