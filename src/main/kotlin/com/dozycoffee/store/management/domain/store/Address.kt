package com.dozycoffee.store.management.domain.store

/**
 * 매장 주소. 도로명 주소, 상세 주소, 우편번호로 이루어지며, 매장 목록을 지역으로 거를 수 있도록 시·도와 시·군·구를 함께 가진다(요구사항 1.1, 1.4).
 *
 * 상세 주소는 없을 수 있다(`null`). 값이 있다면 공백뿐일 수 없다. 항목마다 길이 상한이 있다([StoreInputLimits]).
 * 형식(우편번호 자릿수, 시·도 이름 등)은 검증하지 않는다.
 */
data class Address(
    val roadAddress: String,
    val detailAddress: String?,
    val postalCode: String,
    val sido: String,
    val sigungu: String,
) {
    init {
        require(roadAddress.isNotBlank()) { "도로명 주소는 비어 있을 수 없습니다" }
        requireMaxLength("도로명 주소", roadAddress, StoreInputLimits.ROAD_ADDRESS_MAX)
        require(detailAddress == null || detailAddress.isNotBlank()) { "상세 주소는 없거나 공백뿐일 수 없습니다" }
        if (detailAddress != null) requireMaxLength("상세 주소", detailAddress, StoreInputLimits.DETAIL_ADDRESS_MAX)
        require(postalCode.isNotBlank()) { "우편번호는 비어 있을 수 없습니다" }
        requireMaxLength("우편번호", postalCode, StoreInputLimits.POSTAL_CODE_MAX)
        require(sido.isNotBlank()) { "시·도는 비어 있을 수 없습니다" }
        requireMaxLength("시·도", sido, StoreInputLimits.SIDO_MAX)
        require(sigungu.isNotBlank()) { "시·군·구는 비어 있을 수 없습니다" }
        requireMaxLength("시·군·구", sigungu, StoreInputLimits.SIGUNGU_MAX)
    }
}
