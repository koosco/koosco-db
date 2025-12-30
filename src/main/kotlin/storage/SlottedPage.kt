package com.koosco.storage

import java.nio.ByteBuffer

/**
 * fileName       : SlottedPage
 * author         : koo
 * date           : 2025. 12. 31. 오전 4:34
 * description    : 하나의 Page에 대응되는 가상 메모리
 */
class SlottedPage(
    private val buffer: ByteBuffer
) {
    companion object {
        const val HEADER_SIZE = 6 // pageType(2) + freeSpaceOffset(2) + slotCount(2)
        const val SLOT_SIZE = 4   // offset(short 2) + length(short 2)

        // Header 내부에서 각 필드가 시작되는 위치
        private const val OFFSET_PAGE_TYPE = 0
        private const val OFFSET_FREE_SPACE = 2
        private const val OFFSET_SLOT_COUNT = 4
    }

    init {
        // 4KB 페이지 크기 확인
        require(buffer.capacity() == Page.PAGE_SIZE)
    }

    /**
     * 페이지 초기화
     * - slot 없음
     * - record 없음
     * - free space = 페이지 맨 끝
     */
    fun init() {
        buffer.clear()

        setPageType(1) // pageType을 1로 고정, e.g. heap page TODO: index page, catalog page 등 구분 필요시 변경
        setSlotCount(0) // 슬롯 없음
        setFreeSpaceOffset(Page.PAGE_SIZE) // record는 페이지 끝에서부터 채워지므로, freeSpaceOffset은 페이지 맨 끝

        buffer.clear() // Header 작성 후 position 0으로 복귀
    }

    /**
     * 0번 위치에 2byte로 페이지 타입 저장
     */
    private fun setPageType(type: Short) {
        // position에 무관하게 OFFSET_PAGE_TYPE 위치에 저장
        buffer.putShort(OFFSET_PAGE_TYPE, type)
    }

    private fun getFreeSpaceOffset(): Int = buffer.getShort(OFFSET_FREE_SPACE).toInt()

    private fun setFreeSpaceOffset(value: Int) {
        buffer.putShort(OFFSET_FREE_SPACE, value.toShort())
    }

    // TODO: 캡슐화 필요, e.g. iterator
    fun slotCount(): Int = buffer.getShort(OFFSET_SLOT_COUNT).toInt()

    private fun setSlotCount(value: Int) {
        buffer.putShort(OFFSET_SLOT_COUNT, value.toShort())
    }

    /**
     * 레코드 삽입
     * - record : 아래에서 위로 데이터 저장
     * - slot  : 위에서 아래로 슬롯 저장
     * - slotId = arrayIndex -> RID slotId
     *
     * @return 삽입된 슬롯 인덱스
     */
    fun insert(record: ByteArray): Int {
        val slotCount = slotCount()
        val freeSpace = getFreeSpaceOffset()

        val needed = record.size + SLOT_SIZE // 삽입에 필요한 공간
        val slotDirEnd = HEADER_SIZE + slotCount * SLOT_SIZE // 현재 슬롯 디렉토리의 끝 위치

        if (freeSpace - needed < slotDirEnd) {
            throw IllegalStateException("Not enough space")
        }

        val recordOffset = freeSpace - record.size // 레코드를 저장할 위치

        // write record bytes
        buffer.position(recordOffset) // 레코드를 저장할 위치
        buffer.put(record) // 커서 이동 -> 쓰기

        // write slot
        val slotPos = HEADER_SIZE + slotCount * SLOT_SIZE
        buffer.putShort(slotPos, recordOffset.toShort()) // record offset
        buffer.putShort(slotPos + 2, record.size.toShort()) // record length

        setFreeSpaceOffset(recordOffset)
        setSlotCount(slotCount + 1)

        return slotCount
    }

    fun read(slotId: Int): ByteArray {
        val slotCount = slotCount()
        require(slotId in 0 until slotCount)

        val slotPos = HEADER_SIZE + slotId * SLOT_SIZE
        val offset = buffer.getShort(slotPos).toInt()
        val length = buffer.getShort(slotPos + 2).toInt()

        if (length == 0) {
            throw IllegalStateException("Deleted record")
        }

        val data = ByteArray(length)
        buffer.position(offset)
        buffer.get(data)

        return data
    }

    /**
     * TODO: 실제로 공간을 회수하지는 않음
     */
    fun delete(slotId: Int) {
        val slotPos = HEADER_SIZE + slotId * SLOT_SIZE
        buffer.putShort(slotPos + 2, 0) // length = 0
    }
}
