package com.koosco.common

/**
 * fileName       : Types
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:52
 * description    :
 */
typealias PageId = Long
typealias FrameId = Int
typealias TxId = Long
typealias Lsn = Long

data class Rid(
    val pageId: PageId,
    val slotId: Int
)
