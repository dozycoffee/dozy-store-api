package com.dozycoffee.store.common.time

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

@ConfigurationProperties("store")
data class StoreProperties(
    /** 업무 기준 시간대. 개점일·폐점일의 "오늘"을 이 시간대로 해석한다. */
    val businessZone: ZoneId,
)
