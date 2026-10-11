package com.dozycoffee.store.common.time

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals

@DisplayName("BusinessCalendar")
class BusinessCalendarTest {
    private fun calendar(utc: String) =
        BusinessCalendar(
            Clock.fixed(Instant.parse(utc), ZoneOffset.UTC),
            StoreProperties(ZoneId.of("Asia/Seoul")),
        )

    @Test
    fun `UTC로는 전날이어도 업무 시간대의 오늘을 돌려준다`() {
        assertEquals(LocalDate.of(2026, 10, 11), calendar("2026-10-10T16:00:00Z").today())
    }

    @Test
    fun `업무 시간대 자정 직전은 아직 그날이다`() {
        assertEquals(LocalDate.of(2026, 10, 10), calendar("2026-10-10T14:59:59Z").today())
    }
}
