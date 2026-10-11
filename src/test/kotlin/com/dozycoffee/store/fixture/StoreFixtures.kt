package com.dozycoffee.store.fixture

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.NewStore
import com.dozycoffee.store.management.domain.store.Store
import com.dozycoffee.store.management.domain.store.StoreCode
import com.dozycoffee.store.management.domain.store.StoreStatus
import java.time.LocalDate

fun address(
    roadAddress: String = "서울특별시 강남구 강남대로 396",
    detailAddress: String? = "1층",
    postalCode: String = "06232",
    sido: String = "서울특별시",
    sigungu: String = "강남구",
) = Address(roadAddress, detailAddress, postalCode, sido, sigungu)

/** 등록하려는 매장. 기본값은 유효한 최소 상태다. */
fun newStore(
    code: StoreCode = StoreCode.create(101),
    name: String = "강남역점",
    address: Address = address(),
    phone: String = "02-1234-5678",
    openingDate: LocalDate = LocalDate.of(2026, 11, 1),
) = NewStore(code, name, address, phone, openingDate)

/** 저장된 매장. 기본값은 등록 직후인 `PREPARING`이다. 폐점 매장은 `closingDate`를 함께 준다. */
fun store(
    id: StoreId = StoreId(101),
    code: StoreCode = StoreCode.create(101),
    name: String = "강남역점",
    address: Address = address(),
    phone: String = "02-1234-5678",
    status: StoreStatus = StoreStatus.PREPARING,
    openingDate: LocalDate = LocalDate.of(2026, 11, 1),
    closingDate: LocalDate? = null,
    version: Long = 0,
) = Store.reconstitute(id, code, name, address, phone, status, openingDate, closingDate, version)
