package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.StoreInputLimits

/** 주소 요청. 값을 모두 널 허용으로 받아 어느 필드가 문제인지 [RequestValues]가 필드 이름과 함께 알린다. */
data class AddressRequest(
    val roadAddress: String?,
    val detailAddress: String?,
    val postalCode: String?,
    val sido: String?,
    val sigungu: String?,
) {
    /** [field]는 이 주소를 가진 요청 필드의 이름이다. 오류의 필드 이름이 `address.roadAddress`처럼 이어진다. */
    fun toDomain(field: String): Address =
        Address(
            roadAddress = RequestValues.text("$field.roadAddress", roadAddress, StoreInputLimits.ROAD_ADDRESS_MAX),
            detailAddress = RequestValues.optionalText("$field.detailAddress", detailAddress, StoreInputLimits.DETAIL_ADDRESS_MAX),
            postalCode = RequestValues.text("$field.postalCode", postalCode, StoreInputLimits.POSTAL_CODE_MAX),
            sido = RequestValues.text("$field.sido", sido, StoreInputLimits.SIDO_MAX),
            sigungu = RequestValues.text("$field.sigungu", sigungu, StoreInputLimits.SIGUNGU_MAX),
        )
}
