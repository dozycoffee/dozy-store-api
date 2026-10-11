package com.dozycoffee.store.management.application.service

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.management.adapter.outbound.persistence.StoresTable
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.AddressResult
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("StoreService")
class StoreServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var registerStore: RegisterStoreUseCase

    @Autowired
    private lateinit var getStore: GetStoreUseCase

    private fun command(
        name: String = "강남역점",
        openingDate: LocalDate = LocalDate.of(2026, 11, 1),
    ) = RegisterStoreCommand(name, address(), "02-1234-5678", openingDate)

    /** 서비스가 자기 트랜잭션을 열고 닫는다. 테스트는 그 바깥에서 직접 호출해 경계를 그대로 검증한다. */
    private fun <T> call(block: suspend () -> T): T = runBlocking { block() }

    private fun storeCount(): Int = tx { StoresTable.selectAll().count().toInt() }

    private fun insertStoreSql(
        code: String,
        name: String,
        status: String,
        closingDate: String? = null,
    ) = """
        INSERT INTO stores (code, name, road_address, postal_code, sido, sigungu, phone, status, opening_date, closing_date)
        VALUES ('$code', '$name', '도로명', '06232', '서울특별시', '강남구', '02-1234-5678', '$status', '2026-11-01',
                ${closingDate?.let { "'$it'" } ?: "NULL"})
        """.trimIndent()

    @Nested
    @DisplayName("S1 기본 흐름: 매장 등록")
    inner class Register {
        @Test
        fun `매장은 개점 준비 상태로 등록되고 매장 코드가 부여된다`() {
            val result = call { registerStore.register(command()) }

            assertEquals(StoreStatusResult.PREPARING, result.status)
            assertTrue(Regex("ST-[0-9]{8}").matches(result.code), result.code)
            assertNull(result.closingDate)
            assertEquals(0, result.version)
        }

        @Test
        fun `입력한 매장 정보와 개점 예정일이 그대로 저장된다`() {
            val result =
                call {
                    registerStore.register(
                        RegisterStoreCommand(
                            name = "강남역점",
                            address = address(detailAddress = null),
                            phone = "02-1234-5678",
                            openingDate = LocalDate.of(2026, 11, 1),
                        ),
                    )
                }

            assertEquals("강남역점", result.name)
            assertEquals(
                AddressResult("서울특별시 강남구 강남대로 396", null, "06232", "서울특별시", "강남구"),
                result.address,
            )
            assertEquals("02-1234-5678", result.phone)
            assertEquals(LocalDate.of(2026, 11, 1), result.openingDate)
        }

        @Test
        fun `등록한 매장은 저장되어 같은 값으로 조회된다`() {
            val registered = call { registerStore.register(command()) }

            assertEquals(registered, call { getStore.get(registered.storeId) })
        }

        @Test
        fun `매장마다 서로 다른 매장 코드가 부여된다`() {
            val first = call { registerStore.register(command(name = "강남역점")) }
            val second = call { registerStore.register(command(name = "역삼점")) }

            assertNotEquals(first.code, second.code)
            assertNotEquals(first.storeId, second.storeId)
        }
    }

    @Nested
    @DisplayName("S1 예외 흐름 1b: 이름 중복")
    inner class DuplicatedName {
        @Test
        fun `폐점하지 않은 다른 매장과 이름이 같으면 거부하고 저장하지 않는다`() {
            call { registerStore.register(command(name = "강남역점")) }

            assertFailsWithDuplicated { call { registerStore.register(command(name = "강남역점")) } }

            assertEquals(1, storeCount())
        }

        @ParameterizedTest
        @EnumSource(
            value = StoreStatus::class,
            names = ["CLOSED"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `폐점하지 않은 어느 상태의 매장과도 이름이 겹치면 거부한다`(status: StoreStatus) {
            execute(insertStoreSql("ST-00000001", "강남역점", status.name))

            assertFailsWithDuplicated { call { registerStore.register(command(name = "강남역점")) } }

            assertEquals(1, storeCount())
        }

        @Test
        fun `폐점한 매장의 이름은 새 매장에 다시 쓸 수 있다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = "2027-03-31"))

            val result = call { registerStore.register(command(name = "강남역점")) }

            assertEquals("강남역점", result.name)
            assertEquals(StoreStatusResult.PREPARING, result.status)
            assertEquals(2, storeCount())
        }

        @Test
        fun `같은 이름으로 동시에 등록하면 하나만 성공한다`() {
            val attempts = 8

            val results =
                runBlocking {
                    (1..attempts)
                        .map {
                            async(Dispatchers.Default) { runCatching { registerStore.register(command(name = "동시등록점")) } }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is StoreNameDuplicatedException })
            assertEquals(1, storeCount())
        }

        private fun assertFailsWithDuplicated(block: () -> Unit) {
            val e = runCatching(block).exceptionOrNull()
            assertTrue(e is StoreNameDuplicatedException, "StoreNameDuplicatedException이어야 합니다: $e")
        }
    }

    @Nested
    @DisplayName("매장 조회")
    inner class Get {
        @Test
        fun `없는 매장은 찾을 수 없다`() {
            val e = runCatching { call { getStore.get(StoreId(999)) } }.exceptionOrNull()

            assertTrue(e is StoreNotFoundException, "StoreNotFoundException이어야 합니다: $e")
        }

        @Test
        fun `폐점한 매장도 조회된다`() {
            execute(insertStoreSql("ST-00000001", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            val result = call { getStore.get(StoreId(1)) }

            assertEquals(StoreStatusResult.CLOSED, result.status)
            assertEquals(LocalDate.of(2027, 3, 31), result.closingDate)
        }
    }
}
