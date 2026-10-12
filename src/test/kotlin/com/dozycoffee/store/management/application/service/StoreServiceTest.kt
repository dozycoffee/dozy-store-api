package com.dozycoffee.store.management.application.service

import com.dozycoffee.store.common.paging.PageRequest
import com.dozycoffee.store.common.time.BusinessCalendar
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.core.VersionConflictException
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.management.adapter.outbound.persistence.StoresTable
import com.dozycoffee.store.management.application.port.inbound.ChangeStoreStatusUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.SearchStoresUseCase
import com.dozycoffee.store.management.application.port.inbound.UpdateStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.command.SearchStoresQuery
import com.dozycoffee.store.management.application.port.inbound.command.UpdateStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.AddressResult
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.InvalidStoreStatusTransitionException
import com.dozycoffee.store.management.domain.store.exception.OpeningDateNotChangeableException
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

    @Autowired
    private lateinit var updateStore: UpdateStoreUseCase

    @Autowired
    private lateinit var changeStatus: ChangeStoreStatusUseCase

    @Autowired
    private lateinit var searchStores: SearchStoresUseCase

    @Autowired
    private lateinit var calendar: BusinessCalendar

    private fun command(
        name: String = "강남역점",
        openingDate: LocalDate = LocalDate.of(2026, 11, 1),
    ) = RegisterStoreCommand(name, address(), "02-1234-5678", openingDate)

    /** 서비스가 자기 트랜잭션을 열고 닫는다. 테스트는 그 바깥에서 직접 호출해 경계를 그대로 검증한다. */
    private fun <T> call(block: suspend () -> T): T = runBlocking { block() }

    private fun storeCount(): Int = tx { StoresTable.selectAll().count().toInt() }

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

    @Nested
    @DisplayName("매장 정보 수정")
    inner class Update {
        private val openingDate = LocalDate.of(2026, 11, 1)

        private fun updateCommand(
            id: Long = 1,
            expectedVersion: Long = 0,
            name: String = "서초점",
            address: Address = address(sido = "서울특별시", sigungu = "서초구", detailAddress = null),
            phone: String = "02-9999-0000",
            openingDate: LocalDate = this.openingDate,
        ) = UpdateStoreCommand(StoreId(id), expectedVersion, name, address, phone, openingDate)

        private fun registerStore1(name: String = "강남역점") = call { registerStore.register(command(name = name)) }

        @Test
        fun `매장 정보를 수정하면 저장되고 버전이 1 오른다`() {
            val registered = registerStore1()

            val result = call { updateStore.update(updateCommand(expectedVersion = registered.version)) }

            assertEquals("서초점", result.name)
            assertEquals("서초구", result.address.sigungu)
            assertNull(result.address.detailAddress)
            assertEquals("02-9999-0000", result.phone)
            assertEquals(1, result.version)
            assertEquals(result, call { getStore.get(registered.storeId) })
        }

        @Test
        fun `매장 코드와 상태는 수정으로 바뀌지 않는다`() {
            val registered = registerStore1()

            val result = call { updateStore.update(updateCommand()) }

            assertEquals(registered.code, result.code)
            assertEquals(StoreStatusResult.PREPARING, result.status)
            assertNull(result.closingDate)
        }

        @Test
        fun `개점 준비 중인 매장은 개점 예정일을 바꿀 수 있다`() {
            registerStore1()

            val result = call { updateStore.update(updateCommand(openingDate = LocalDate.of(2026, 12, 1))) }

            assertEquals(LocalDate.of(2026, 12, 1), result.openingDate)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비가 아니면 개점일을 바꾸려 할 때 거부하고 저장된 값은 그대로다`(status: StoreStatus) {
            execute(insertStoreSql("ST-00000001", "강남역점", status.name, closingDate = "2027-03-31".takeIf { status == StoreStatus.CLOSED }))

            val e = runCatching { call { updateStore.update(updateCommand(openingDate = LocalDate.of(2026, 12, 1))) } }.exceptionOrNull()

            assertTrue(e is OpeningDateNotChangeableException, "OpeningDateNotChangeableException이어야 합니다: $e")
            val stored = call { getStore.get(StoreId(1)) }
            assertEquals("강남역점", stored.name)
            assertEquals(openingDate, stored.openingDate)
            assertEquals(0, stored.version)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비가 아니어도 개점일을 그대로 두면 다른 정보는 수정할 수 있다 (폐점한 매장 포함)`(status: StoreStatus) {
            execute(insertStoreSql("ST-00000001", "강남역점", status.name, closingDate = "2027-03-31".takeIf { status == StoreStatus.CLOSED }))

            val result = call { updateStore.update(updateCommand(openingDate = openingDate)) }

            assertEquals("서초점", result.name)
            assertEquals(status.name, result.status.name)
        }

        @Test
        fun `폐점하지 않은 다른 매장과 이름이 같으면 거부하고 저장된 값은 그대로다`() {
            registerStore1(name = "강남역점")
            call { registerStore.register(command(name = "역삼점")) }

            val e = runCatching { call { updateStore.update(updateCommand(id = 2, name = "강남역점")) } }.exceptionOrNull()

            assertTrue(e is StoreNameDuplicatedException, "StoreNameDuplicatedException이어야 합니다: $e")
            val stored = call { getStore.get(StoreId(2)) }
            assertEquals("역삼점", stored.name)
            assertEquals(0, stored.version)
        }

        @Test
        fun `이름을 그대로 두고 다른 정보만 고치는 것은 이름 중복이 아니다`() {
            registerStore1(name = "강남역점")

            val result = call { updateStore.update(updateCommand(name = "강남역점")) }

            assertEquals("강남역점", result.name)
            assertEquals("02-9999-0000", result.phone)
        }

        @Test
        fun `폐점한 매장의 이름으로는 바꿀 수 있다`() {
            execute(insertStoreSql("ST-00000009", "옛점", "CLOSED", closingDate = "2027-03-31"))
            registerStore1()

            val result = call { updateStore.update(updateCommand(id = 2, name = "옛점")) }

            assertEquals("옛점", result.name)
        }

        @Test
        fun `없는 매장은 찾을 수 없다`() {
            val e = runCatching { call { updateStore.update(updateCommand(id = 999)) } }.exceptionOrNull()

            assertTrue(e is StoreNotFoundException, "StoreNotFoundException이어야 합니다: $e")
        }

        @Test
        fun `화면이 본 뒤에 다른 수정이 반영됐으면 거부하고 현재 버전을 알려 준다`() {
            registerStore1()
            call { updateStore.update(updateCommand(name = "서초점", expectedVersion = 0)) }

            val e = runCatching { call { updateStore.update(updateCommand(name = "역삼점", expectedVersion = 0)) } }.exceptionOrNull()

            assertTrue(e is VersionConflictException, "VersionConflictException이어야 합니다: $e")
            assertEquals(1, e.currentVersion)
            assertEquals("서초점", call { getStore.get(StoreId(1)) }.name)
        }

        @Test
        fun `이전 결과의 버전으로 이어서 수정할 수 있다`() {
            registerStore1()

            val first = call { updateStore.update(updateCommand(name = "서초점", expectedVersion = 0)) }
            val second = call { updateStore.update(updateCommand(name = "역삼점", expectedVersion = first.version)) }

            assertEquals(1, first.version)
            assertEquals(2, second.version)
            assertEquals("역삼점", call { getStore.get(StoreId(1)) }.name)
        }

        @Test
        fun `같은 버전으로 동시에 수정하면 하나만 성공한다`() {
            registerStore1()
            val attempts = 6

            val results =
                runBlocking {
                    (1..attempts)
                        .map { n ->
                            async(
                                Dispatchers.Default,
                            ) { runCatching { updateStore.update(updateCommand(name = "지점$n", expectedVersion = 0)) } }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is VersionConflictException })
            assertEquals(1, call { getStore.get(StoreId(1)) }.version)
        }
    }

    @Nested
    @DisplayName("상태 전환 (S1 개점, S2 휴업·재개·폐점)")
    inner class StatusChange {
        private val openingDate = LocalDate.of(2026, 11, 1)

        private fun storeIn(status: StoreStatus) =
            execute(insertStoreSql("ST-00000001", "강남역점", status.name, closingDate = "2027-03-31".takeIf { status == StoreStatus.CLOSED }))

        private fun stored() = call { getStore.get(StoreId(1)) }

        private fun assertRejected(
            status: StoreStatus,
            action: suspend () -> Any,
        ) {
            storeIn(status)

            val e = runCatching { call { action() } }.exceptionOrNull()

            assertTrue(e is InvalidStoreStatusTransitionException, "InvalidStoreStatusTransitionException이어야 합니다: $e")
            val after = stored()
            assertEquals(status.name, after.status.name)
            assertEquals(openingDate, after.openingDate)
            assertEquals(0, after.version)
        }

        @Test
        fun `개점하면 영업 중이 되고 개점일이 오늘로 바뀐다`() {
            storeIn(StoreStatus.PREPARING)
            val today = calendar.today()

            val result = call { changeStatus.open(StoreId(1)) }

            assertEquals(StoreStatusResult.OPEN, result.status)
            assertEquals(today, result.openingDate)
            assertNull(result.closingDate)
            assertEquals(1, result.version)
            assertEquals(result, stored())
        }

        @Test
        fun `등록한 매장을 이어서 개점하면 매니저가 준비한 매장이 영업을 시작한다`() {
            val registered = call { registerStore.register(command()) }

            val opened = call { changeStatus.open(registered.storeId) }

            assertEquals(registered.code, opened.code)
            assertEquals(registered.name, opened.name)
            assertEquals(StoreStatusResult.OPEN, opened.status)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING"], mode = EnumSource.Mode.EXCLUDE)
        fun `개점 준비가 아니면 개점을 거부하고 저장된 값은 그대로다`(status: StoreStatus) = assertRejected(status) { changeStatus.open(StoreId(1)) }

        @Test
        fun `휴업하면 휴업 상태가 되고 개점일은 그대로다`() {
            storeIn(StoreStatus.OPEN)

            val result = call { changeStatus.suspend(StoreId(1)) }

            assertEquals(StoreStatusResult.SUSPENDED, result.status)
            assertEquals(openingDate, result.openingDate)
            assertEquals(1, result.version)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["OPEN"], mode = EnumSource.Mode.EXCLUDE)
        fun `영업 중이 아니면 휴업을 거부하고 저장된 값은 그대로다`(status: StoreStatus) = assertRejected(status) { changeStatus.suspend(StoreId(1)) }

        @Test
        fun `재개하면 영업 중이 되고 개점일은 그대로다`() {
            storeIn(StoreStatus.SUSPENDED)

            val result = call { changeStatus.resume(StoreId(1)) }

            assertEquals(StoreStatusResult.OPEN, result.status)
            assertEquals(openingDate, result.openingDate)
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["SUSPENDED"], mode = EnumSource.Mode.EXCLUDE)
        fun `휴업이 아니면 재개를 거부하고 저장된 값은 그대로다`(status: StoreStatus) = assertRejected(status) { changeStatus.resume(StoreId(1)) }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["PREPARING", "OPEN", "SUSPENDED"])
        fun `폐점하지 않은 매장은 어느 상태에서든 폐점할 수 있고 폐점일이 오늘로 기록된다`(status: StoreStatus) {
            storeIn(status)
            val today = calendar.today()

            val result = call { changeStatus.close(StoreId(1)) }

            assertEquals(StoreStatusResult.CLOSED, result.status)
            assertEquals(today, result.closingDate)
            assertEquals(openingDate, result.openingDate)
            assertEquals(result, stored())
        }

        @Test
        fun `폐점한 매장은 다시 열 수 없고 어떤 전환도 거부한다`() {
            storeIn(StoreStatus.CLOSED)

            listOf<suspend () -> Any>(
                { changeStatus.open(StoreId(1)) },
                { changeStatus.suspend(StoreId(1)) },
                { changeStatus.resume(StoreId(1)) },
                { changeStatus.close(StoreId(1)) },
            ).forEach { action ->
                val e = runCatching { call { action() } }.exceptionOrNull()
                assertTrue(e is InvalidStoreStatusTransitionException, "InvalidStoreStatusTransitionException이어야 합니다: $e")
            }
            assertEquals(StoreStatusResult.CLOSED, stored().status)
            assertEquals(LocalDate.of(2027, 3, 31), stored().closingDate)
        }

        @Test
        fun `없는 매장은 찾을 수 없다`() {
            val e = runCatching { call { changeStatus.open(StoreId(999)) } }.exceptionOrNull()

            assertTrue(e is StoreNotFoundException, "StoreNotFoundException이어야 합니다: $e")
        }

        @Test
        fun `폐점한 매장의 이름은 새 매장에 다시 쓸 수 있다`() {
            val registered = call { registerStore.register(command(name = "강남역점")) }

            call { changeStatus.close(registered.storeId) }
            val reused = call { registerStore.register(command(name = "강남역점")) }

            assertEquals("강남역점", reused.name)
            assertEquals(StoreStatusResult.PREPARING, reused.status)
        }

        @Test
        fun `상태가 바뀌면 그 전에 본 버전의 정보 수정은 충돌로 거부된다`() {
            storeIn(StoreStatus.PREPARING)
            val seenVersion = stored().version
            call { changeStatus.open(StoreId(1)) }

            val e =
                runCatching {
                    call {
                        updateStore.update(UpdateStoreCommand(StoreId(1), seenVersion, "서초점", address(), "02-9999-0000", openingDate))
                    }
                }.exceptionOrNull()

            assertTrue(e is VersionConflictException, "VersionConflictException이어야 합니다: $e")
            assertEquals(1, e.currentVersion)
            assertEquals("강남역점", stored().name)
        }

        @Test
        fun `같은 매장의 개점이 동시에 와도 한 번만 전이된다`() {
            storeIn(StoreStatus.PREPARING)
            val attempts = 6

            val results =
                runBlocking {
                    (1..attempts).map { async(Dispatchers.Default) { runCatching { changeStatus.open(StoreId(1)) } } }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is InvalidStoreStatusTransitionException })
            assertEquals(1, stored().version)
            assertEquals(StoreStatusResult.OPEN, stored().status)
        }
    }

    @Nested
    @DisplayName("매장 목록 검색 (S1·S7: 판매 범위 대상 고르기, 지역과 이름으로 찾기)")
    inner class Search {
        private fun seed() {
            execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", sido = "서울특별시", sigungu = "강남구"))
            execute(insertStoreSql("ST-00000002", "역삼점", "PREPARING", sido = "서울특별시", sigungu = "강남구"))
            execute(insertStoreSql("ST-00000003", "해운대점", "SUSPENDED", sido = "부산광역시", sigungu = "해운대구"))
            execute(insertStoreSql("ST-00000004", "옛점", "CLOSED", closingDate = "2027-03-31"))
        }

        private fun search(query: SearchStoresQuery = SearchStoresQuery()) = call { searchStores.search(query) }

        @Test
        fun `판매 범위 대상 고르기는 기본 조회로 폐점하지 않은 모든 매장을 이름순으로 본다`() {
            seed()

            val page = search()

            assertEquals(listOf("강남역점", "역삼점", "해운대점"), page.content.map { it.name })
            assertEquals(3, page.totalElements)
        }

        @Test
        fun `지역과 이름을 함께 주면 둘 다 만족하는 매장만 나온다`() {
            seed()

            val page = search(SearchStoresQuery(sido = "서울특별시", sigungu = "강남구", nameContains = "삼"))

            assertEquals(listOf("역삼점"), page.content.map { it.name })
        }

        @Test
        fun `상태로 폐점을 지정하면 폐점한 매장을 볼 수 있다`() {
            seed()

            val page = search(SearchStoresQuery(statuses = setOf(StoreStatusResult.CLOSED)))

            val store = page.content.single()
            assertEquals("옛점", store.name)
            assertEquals(StoreStatusResult.CLOSED, store.status)
            assertEquals(LocalDate.of(2027, 3, 31), store.closingDate)
        }

        @Test
        fun `상태를 결과 타입으로 받아 모든 상태를 거를 수 있다`() {
            seed()

            StoreStatusResult.entries
                .filter { it != StoreStatusResult.CLOSED }
                .forEach { status ->
                    val page = search(SearchStoresQuery(statuses = setOf(status)))
                    assertEquals(setOf(status), page.content.map { it.status }.toSet(), "상태 $status")
                    assertEquals(1, page.totalElements)
                }
        }

        @Test
        fun `결과는 등록·조회와 같은 모양의 매장 결과다`() {
            val registered = call { registerStore.register(command()) }

            val found = search().content.single()

            assertEquals(registered, found)
        }

        @Test
        fun `나눠서 조회하고 페이지 정보를 돌려준다`() {
            (1..25).forEach { n -> execute(insertStoreSql("ST-%08d".format(n), "지점%02d".format(n))) }

            val page = search(SearchStoresQuery(page = PageRequest(page = 2, size = 10)))

            assertEquals(5, page.content.size)
            assertEquals(2, page.page)
            assertEquals(10, page.size)
            assertEquals(25, page.totalElements)
            assertEquals(3, page.totalPages)
        }

        @Test
        fun `조건에 맞는 매장이 없으면 빈 페이지다`() {
            seed()

            val page = search(SearchStoresQuery(nameContains = "없는이름"))

            assertTrue(page.content.isEmpty())
            assertEquals(0, page.totalElements)
        }
    }
}
