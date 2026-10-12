package com.dozycoffee.store.management.domain.store

/**
 * 매장 정보 입력의 길이 상한(요구사항 1.1). domain이 `require`로 강제하고, web이 같은 값으로 요청 오류(400)를 만든다.
 * 길이는 `String.length`(UTF-16 코드 단위)로 센다. 한글과 영문은 한 글자가 1이고, 이모지 같은 보충 문자는 2로 센다.
 */
object StoreInputLimits {
    const val NAME_MAX = 50
    const val ROAD_ADDRESS_MAX = 100
    const val DETAIL_ADDRESS_MAX = 100
    const val POSTAL_CODE_MAX = 10
    const val SIDO_MAX = 30
    const val SIGUNGU_MAX = 30
    const val PHONE_MAX = 20
}
