package com.dozycoffee.store.common.time

import java.time.Clock
import java.time.LocalDate

/** 도메인은 시계를 모른다. application이 업무 시간대의 오늘을 구해 도메인 메서드에 넘긴다. */
class BusinessCalendar(
    private val clock: Clock,
    properties: StoreProperties,
) {
    private val zone = properties.businessZone

    fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
}
