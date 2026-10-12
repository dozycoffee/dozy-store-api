package com.dozycoffee.store.management.application.service

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.management.application.port.inbound.ChangeStoreStatusUseCase
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAssignabilityUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.UnassignableReason
import com.dozycoffee.store.management.application.port.inbound.result.UnassignableStoreResult
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("StoreAssignabilityService")
class StoreAssignabilityServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var checkAssignability: CheckStoreAssignabilityUseCase

    @Autowired
    private lateinit var registerStore: RegisterStoreUseCase

    @Autowired
    private lateinit var changeStatus: ChangeStoreStatusUseCase

    private fun check(vararg ids: Long) = runBlocking { checkAssignability.check(ids.map { StoreId(it) }) }

    private fun notFound(id: Long) = UnassignableStoreResult(StoreId(id), UnassignableReason.NOT_FOUND)

    private fun closed(id: Long) = UnassignableStoreResult(StoreId(id), UnassignableReason.CLOSED)

    @Nested
    @DisplayName("지정할 수 있는 매장")
    inner class Assignable {
        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["CLOSED"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비, 영업 중, 휴업 매장은 지정할 수 있다`(status: StoreStatus) {
            execute(insertStoreSql("ST-00000001", "강남역점", status.name))

            assertTrue(check(1).isEmpty())
        }

        @Test
        fun `모두 지정할 수 있으면 빈 목록이다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
            execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
            execute(insertStoreSql("ST-00000003", "서초점", "SUSPENDED"))

            assertTrue(check(1, 2, 3).isEmpty())
        }
    }

    @Nested
    @DisplayName("지정할 수 없는 매장")
    inner class Unassignable {
        @Test
        fun `없는 매장은 NOT_FOUND다`() {
            assertEquals(listOf(notFound(999)), check(999))
        }

        @Test
        fun `폐점한 매장은 CLOSED다`() {
            execute(insertStoreSql("ST-00000001", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            assertEquals(listOf(closed(1)), check(1))
        }

        @Test
        fun `정상 매장과 섞여 있으면 지정할 수 없는 매장만 이유와 함께 돌려준다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
            execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
            execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            assertEquals(listOf(closed(3), notFound(999)), check(1, 2, 3, 999))
        }

        @Test
        fun `결과는 요청한 ID 순서를 따른다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = "2027-03-31"))
            execute(insertStoreSql("ST-00000002", "역삼점", "CLOSED", closingDate = "2027-03-31"))

            assertEquals(listOf(notFound(999), closed(2), closed(1), notFound(500)), check(999, 2, 1, 500))
        }

        @Test
        fun `같은 ID가 여러 번 와도 한 번만 판단한다`() {
            execute(insertStoreSql("ST-00000001", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            assertEquals(listOf(closed(1), notFound(999)), check(1, 1, 999, 1, 999))
        }
    }

    @Nested
    @DisplayName("S2 폐점 이후: 폐점한 매장을 판매 범위에 새로 넣으려 할 때")
    inner class AfterClosing {
        @Test
        fun `개점하고 휴업한 매장은 지정할 수 있지만 폐점하면 지정할 수 없다`() {
            val store =
                runBlocking {
                    registerStore.register(RegisterStoreCommand("강남역점", address(), "02-1234-5678", LocalDate.of(2026, 11, 1)))
                }
            val id = store.storeId.value
            assertTrue(check(id).isEmpty())

            runBlocking { changeStatus.open(store.storeId) }
            assertTrue(check(id).isEmpty())

            runBlocking { changeStatus.suspend(store.storeId) }
            assertTrue(check(id).isEmpty())

            runBlocking { changeStatus.close(store.storeId) }
            assertEquals(listOf(closed(id)), check(id))
        }

        @Test
        fun `폐점한 매장의 이름을 쓰는 새 매장은 다른 매장이라 지정할 수 있다`() {
            val old =
                runBlocking { registerStore.register(RegisterStoreCommand("강남역점", address(), "02-1234-5678", LocalDate.of(2026, 11, 1))) }
            runBlocking { changeStatus.close(old.storeId) }
            val reopened =
                runBlocking { registerStore.register(RegisterStoreCommand("강남역점", address(), "02-1234-5678", LocalDate.of(2027, 4, 1))) }

            assertEquals(listOf(closed(old.storeId.value)), check(old.storeId.value, reopened.storeId.value))
        }
    }

    @Nested
    @DisplayName("요청 개수")
    inner class Size {
        @Test
        fun `1000개까지 한 번에 확인한다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "OPEN"))
            execute(insertStoreSql("ST-00000002", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            val result = check(*(1L..1000L).toList().toLongArray())

            assertEquals(999, result.size)
            assertEquals(closed(2), result.first())
            assertTrue(result.drop(1).all { it.reason == UnassignableReason.NOT_FOUND })
        }

        @Test
        fun `비어 있으면 거부한다`() {
            assertFailsWith<IllegalArgumentException> { check() }
        }

        @Test
        fun `1000개를 넘으면 거부한다`() {
            assertFailsWith<IllegalArgumentException> { check(*(1L..1001L).toList().toLongArray()) }
        }

        @Test
        fun `같은 ID를 반복해 1000개를 넘기는 요청도 개수로 거부한다`() {
            assertFailsWith<IllegalArgumentException> { check(*LongArray(1001) { 1L }) }
        }
    }
}
