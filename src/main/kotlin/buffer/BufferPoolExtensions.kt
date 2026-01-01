package com.koosco.buffer

import com.koosco.common.PageId
import java.nio.ByteBuffer

/**
 * fileName       : BufferFoolExtensions
 * author         : koo
 * date           : 2026. 1. 1. 오후 7:49
 * description    : ByteBuffer "쓰기 모드"로 안전하게 쓰도록 도와주는 유틸
 * - getPage()로 받은 duplicate() buffer는 position/limit가 공유되지 않음
 * - underlying array는 공유되므로, 수정하는 경우 원본 frame도 함께 수정
 */
inline fun BufferPool.withPageForWrite(pageId: PageId, block: (ByteBuffer) -> Unit) {
    val buffer = getPage(pageId)
    block(buffer)
    markDirty(pageId)
}
