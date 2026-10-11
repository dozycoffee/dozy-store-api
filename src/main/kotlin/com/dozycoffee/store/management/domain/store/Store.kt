package com.dozycoffee.store.management.domain.store

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.exception.OpeningDateNotChangeableException
import java.time.LocalDate

/**
 * 저장된 매장. 매장 정보와 영업 상태를 가진다(요구사항 1.1, 1.2).
 * 저장 전의 등록할 매장은 [NewStore]다. 이 클래스는 저장소에서 복원한 [reconstitute]로만 만든다.
 *
 * 변경 메서드는 규칙을 모두 확인한 뒤에 값을 바꾼다. 거부되면 상태가 그대로다.
 */
class Store private constructor(
    val id: StoreId,
    val code: StoreCode,
    name: String,
    address: Address,
    phone: String,
    val status: StoreStatus,
    openingDate: LocalDate,
    val closingDate: LocalDate?,
    val version: Long,
) {
    var name: String = name
        private set

    var address: Address = address
        private set

    var phone: String = phone
        private set

    /** `PREPARING`이면 개점 예정일, 개점한 뒤에는 실제 개점일이다. */
    var openingDate: LocalDate = openingDate
        private set

    init {
        requireStoreName(name)
        requireStorePhone(phone)
        check((status == StoreStatus.CLOSED) == (closingDate != null)) {
            "폐점일은 폐점한 매장에만 있어야 합니다: 매장 $id, 상태 $status, 폐점일 $closingDate"
        }
    }

    /**
     * 매장 정보를 바꾼다. 매장 코드와 상태는 바꾸지 않는다(상태는 전용 전환으로만 바뀐다).
     * 개점 예정일은 `PREPARING`일 때만 바꿀 수 있고, 다른 상태에서는 지금 값과 같아야 한다.
     */
    fun update(
        name: String,
        address: Address,
        phone: String,
        openingDate: LocalDate,
    ) {
        requireStoreName(name)
        requireStorePhone(phone)
        if (status != StoreStatus.PREPARING && openingDate != this.openingDate) {
            throw OpeningDateNotChangeableException(status)
        }
        this.name = name
        this.address = address
        this.phone = phone
        this.openingDate = openingDate
    }

    companion object {
        /** 저장소에서 읽은 값으로 복원한다. 새로 등록하는 매장은 [NewStore]를 쓴다. */
        fun reconstitute(
            id: StoreId,
            code: StoreCode,
            name: String,
            address: Address,
            phone: String,
            status: StoreStatus,
            openingDate: LocalDate,
            closingDate: LocalDate?,
            version: Long,
        ): Store = Store(id, code, name, address, phone, status, openingDate, closingDate, version)
    }
}

internal fun requireStoreName(name: String) = require(name.isNotBlank()) { "매장명은 비어 있을 수 없습니다" }

internal fun requireStorePhone(phone: String) = require(phone.isNotBlank()) { "매장 전화번호는 비어 있을 수 없습니다" }
