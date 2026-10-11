package com.dozycoffee.store.management.domain.store

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.store
import com.dozycoffee.store.management.domain.store.exception.OpeningDateNotChangeableException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("Store")
class StoreTest {
    private val openingDate = LocalDate.of(2026, 11, 1)
    private val closingDate = LocalDate.of(2027, 3, 31)

    @Nested
    @DisplayName("복원할 때")
    inner class Reconstitute {
        @Test
        fun `저장된 값을 그대로 유지한다`() {
            val store =
                Store.reconstitute(
                    id = StoreId(101),
                    code = StoreCode.create(101),
                    name = "강남역점",
                    address = address(),
                    phone = "02-1234-5678",
                    status = StoreStatus.CLOSED,
                    openingDate = openingDate,
                    closingDate = closingDate,
                    version = 3,
                )

            assertEquals(StoreId(101), store.id)
            assertEquals(StoreCode.create(101), store.code)
            assertEquals("강남역점", store.name)
            assertEquals(address(), store.address)
            assertEquals("02-1234-5678", store.phone)
            assertEquals(StoreStatus.CLOSED, store.status)
            assertEquals(openingDate, store.openingDate)
            assertEquals(closingDate, store.closingDate)
            assertEquals(3, store.version)
        }

        @Test
        fun `폐점하지 않은 매장은 폐점일이 없다`() {
            assertNull(store(status = StoreStatus.OPEN).closingDate)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["CLOSED"], mode = EnumSource.Mode.EXCLUDE)
        fun `폐점하지 않은 매장에 폐점일이 있으면 거부한다`(status: StoreStatus) {
            assertFailsWith<IllegalStateException> { store(status = status, closingDate = closingDate) }
        }

        @Test
        fun `폐점한 매장에 폐점일이 없으면 거부한다`() {
            assertFailsWith<IllegalStateException> { store(status = StoreStatus.CLOSED, closingDate = null) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " "])
        fun `매장명이 비어 있으면 거부한다`(name: String) {
            assertFailsWith<IllegalArgumentException> { store(name = name) }
        }
    }

    @Nested
    @DisplayName("정보를 수정할 때")
    inner class Update {
        private val newAddress =
            address(
                roadAddress = "서울특별시 서초구 서초대로 100",
                detailAddress = null,
                postalCode = "06500",
                sigungu = "서초구",
            )

        @Test
        fun `개점 준비 중인 매장은 개점 예정일을 포함해 모든 정보를 바꿀 수 있다`() {
            val store = store(status = StoreStatus.PREPARING, openingDate = openingDate)

            store.update("서초점", newAddress, "02-9999-0000", LocalDate.of(2026, 12, 1))

            assertEquals("서초점", store.name)
            assertEquals(newAddress, store.address)
            assertEquals("02-9999-0000", store.phone)
            assertEquals(LocalDate.of(2026, 12, 1), store.openingDate)
        }

        @Test
        fun `수정해도 매장 코드, 상태, 폐점일, 버전은 바뀌지 않는다`() {
            val store = store(status = StoreStatus.PREPARING, version = 5)

            store.update("서초점", newAddress, "02-9999-0000", LocalDate.of(2026, 12, 1))

            assertEquals(StoreId(101), store.id)
            assertEquals(StoreCode.create(101), store.code)
            assertEquals(StoreStatus.PREPARING, store.status)
            assertNull(store.closingDate)
            assertEquals(5, store.version)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비가 아니어도 개점일을 그대로 두면 다른 정보는 바꿀 수 있다`(status: StoreStatus) {
            val store = store(status = status, openingDate = openingDate, closingDate = closingDateFor(status))

            store.update("서초점", newAddress, "02-9999-0000", openingDate)

            assertEquals("서초점", store.name)
            assertEquals(newAddress, store.address)
            assertEquals("02-9999-0000", store.phone)
            assertEquals(openingDate, store.openingDate)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비가 아니면 개점일을 바꾸려 할 때 거부하고 상태는 그대로다`(status: StoreStatus) {
            val store = store(status = status, openingDate = openingDate, closingDate = closingDateFor(status))
            val original = store.address

            assertFailsWith<OpeningDateNotChangeableException> {
                store.update("서초점", newAddress, "02-9999-0000", LocalDate.of(2026, 12, 1))
            }

            assertEquals("강남역점", store.name)
            assertEquals(original, store.address)
            assertEquals("02-1234-5678", store.phone)
            assertEquals(openingDate, store.openingDate)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " "])
        fun `매장명이 비어 있으면 거부하고 상태는 그대로다`(name: String) {
            val store = store()

            assertFailsWith<IllegalArgumentException> { store.update(name, newAddress, "02-9999-0000", openingDate) }

            assertEquals("강남역점", store.name)
            assertEquals(address(), store.address)
            assertEquals("02-1234-5678", store.phone)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " "])
        fun `전화번호가 비어 있으면 거부하고 상태는 그대로다`(phone: String) {
            val store = store()

            assertFailsWith<IllegalArgumentException> { store.update("서초점", newAddress, phone, openingDate) }

            assertEquals("강남역점", store.name)
            assertEquals(address(), store.address)
            assertEquals("02-1234-5678", store.phone)
        }

        private fun closingDateFor(status: StoreStatus): LocalDate? = closingDate.takeIf { status == StoreStatus.CLOSED }
    }
}
