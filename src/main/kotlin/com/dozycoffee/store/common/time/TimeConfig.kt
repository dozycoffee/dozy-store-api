package com.dozycoffee.store.common.time

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StoreProperties::class)
class TimeConfig {
    /** 시스템 기본 시간대를 쓰지 않는다. 현재 시각은 항상 UTC다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun businessCalendar(
        clock: Clock,
        properties: StoreProperties,
    ): BusinessCalendar = BusinessCalendar(clock, properties)
}
