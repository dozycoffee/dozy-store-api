package com.dozycoffee.store.management.application.service

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.insertManagerSql
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.management.application.port.inbound.AssignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.FindManagedStoresUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersByPrincipalUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.Requester
import com.dozycoffee.store.management.application.port.inbound.StoreRoles
import com.dozycoffee.store.management.application.port.inbound.UnassignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.command.AssignStoreManagerCommand
import com.dozycoffee.store.management.application.port.inbound.command.UnassignStoreManagerCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.manager.exception.InvalidManagerPrincipalTypeException
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerAlreadyAssignedException
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerNotFoundException
import com.dozycoffee.store.management.domain.store.exception.StoreClosedException
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("StoreManagerService")
class StoreManagerServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var assignManager: AssignStoreManagerUseCase

    @Autowired
    private lateinit var unassignManager: UnassignStoreManagerUseCase

    @Autowired
    private lateinit var getManagers: GetStoreManagersUseCase

    @Autowired
    private lateinit var getManagersByPrincipal: GetStoreManagersByPrincipalUseCase

    @Autowired
    private lateinit var findManagedStores: FindManagedStoresUseCase

    @Autowired
    private lateinit var getStore: GetStoreUseCase

    @Autowired
    private lateinit var storeRepository: StoreRepository

    private val open = StoreId(1)
    private val other = StoreId(2)
    private val closed = StoreId(3)

    @BeforeEach
    fun stores() {
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
        execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    /** 서비스가 자기 트랜잭션을 열고 닫는다. 테스트는 그 바깥에서 직접 호출해 경계를 그대로 검증한다. */
    private fun <T> call(block: suspend () -> T): T = runBlocking { block() }

    private fun account(
        n: Long,
        type: PrincipalType = PrincipalType.PARTNER,
    ): PrincipalKey = principalKey(type, UUID(0, n))

    private fun assign(
        storeId: StoreId,
        principal: PrincipalKey,
    ) = call { assignManager.assign(AssignStoreManagerCommand(storeId, principal)) }

    private fun unassign(
        storeId: StoreId,
        principal: PrincipalKey,
    ) = call { unassignManager.unassign(UnassignStoreManagerCommand(storeId, principal)) }

    private fun managersOf(storeId: StoreId) = call { getManagers.getByStore(storeId) }.map { it.principal }

    private inline fun <reified E : Throwable> assertRejected(block: () -> Unit) {
        val e = runCatching(block).exceptionOrNull()
        assertTrue(e is E, "${E::class.simpleName}이어야 합니다: $e")
    }

    @Nested
    @DisplayName("S3 기본 흐름: 매니저 지정")
    inner class Assign {
        @Test
        fun `지정하면 매장의 매니저가 되고 지정 시각이 기록된다`() {
            val result = assign(open, account(1))

            assertEquals(open, result.storeId)
            assertEquals(account(1), result.principal)
            assertEquals(listOf(account(1)), managersOf(open))
        }

        @ParameterizedTest
        @EnumSource(PrincipalType::class, names = ["PARTNER", "EMPLOYEE"])
        fun `파트너와 본사 직원 계정을 지정할 수 있다`(type: PrincipalType) {
            assign(open, account(1, type))

            assertEquals(listOf(account(1, type)), managersOf(open))
        }

        @Test
        fun `매니저가 없는 매장도 있을 수 있다`() {
            assertTrue(managersOf(open).isEmpty())
        }

        @Test
        fun `한 계정은 여러 매장의 매니저가 되고 한 매장에는 여러 계정이 있을 수 있다`() {
            assign(open, account(1))
            assign(other, account(1))
            assign(open, account(2))

            assertEquals(listOf(account(1), account(2)), managersOf(open))
            assertEquals(listOf(account(1)), managersOf(other))
        }

        @Test
        fun `매니저를 교체하면 매장의 정보와 버전은 그대로다`() {
            assign(open, account(1))
            val before = call { getStore.get(open) }

            unassign(open, account(1))
            assign(open, account(2))

            assertEquals(listOf(account(2)), managersOf(open))
            assertEquals(before, call { getStore.get(open) })
        }

        @Test
        fun `영업 중이거나 개점 준비 중인 매장에 지정할 수 있다`() {
            assign(open, account(1))
            assign(other, account(1))

            assertEquals(2, call { getManagersByPrincipal.getByPrincipal(account(1)) }.size)
        }
    }

    @Nested
    @DisplayName("S3 예외 흐름: 지정 거부")
    inner class AssignRejected {
        @Test
        fun `이미 그 매장의 매니저인 계정을 다시 지정하면 거부한다`() {
            assign(open, account(1))

            assertRejected<StoreManagerAlreadyAssignedException> { assign(open, account(1)) }

            assertEquals(listOf(account(1)), managersOf(open))
        }

        @Test
        fun `계정 ID가 같아도 계정 종류가 다르면 다른 계정이라 지정할 수 있다`() {
            assign(open, account(1, PrincipalType.PARTNER))
            assign(open, account(1, PrincipalType.EMPLOYEE))

            assertEquals(2, managersOf(open).size)
        }

        @ParameterizedTest
        @EnumSource(PrincipalType::class, names = ["PARTNER", "EMPLOYEE"], mode = EnumSource.Mode.EXCLUDE)
        fun `서비스 계정과 고객은 지정할 수 없고 저장하지 않는다`(type: PrincipalType) {
            assertRejected<InvalidManagerPrincipalTypeException> { assign(open, account(1, type)) }

            assertTrue(managersOf(open).isEmpty())
        }

        @Test
        fun `계정 종류가 잘못된 요청은 없는 매장이어도 요청 오류로 거부한다`() {
            assertRejected<InvalidManagerPrincipalTypeException> { assign(StoreId(999), account(1, PrincipalType.SYSTEM)) }
        }

        @Test
        fun `없는 매장에는 지정할 수 없다`() {
            assertRejected<StoreNotFoundException> { assign(StoreId(999), account(1)) }
        }

        @Test
        fun `폐점한 매장에는 새로 지정할 수 없다`() {
            assertRejected<StoreClosedException> { assign(closed, account(1)) }

            assertTrue(managersOf(closed).isEmpty())
        }

        @Test
        fun `매장이 폐점해도 이미 있던 지정은 유지되고 새 지정만 막힌다`() {
            assign(open, account(1))

            execute("UPDATE stores SET status = 'CLOSED', closing_date = '2027-03-31' WHERE id = 1")

            assertEquals(listOf(account(1)), managersOf(open))
            assertRejected<StoreClosedException> { assign(open, account(2)) }
            assertEquals(listOf(account(1)), managersOf(open))
        }

        @Test
        fun `같은 계정을 동시에 지정하면 하나만 성공한다`() {
            val attempts = 8

            val results =
                runBlocking {
                    (1..attempts)
                        .map {
                            async(
                                Dispatchers.Default,
                            ) { runCatching { assignManager.assign(AssignStoreManagerCommand(open, account(1))) } }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is StoreManagerAlreadyAssignedException })
            assertEquals(1, managersOf(open).size)
        }

        @Test
        fun `폐점이 진행 중인 매장에 지정하면 폐점이 끝난 뒤 거부된다`() {
            runBlocking {
                val locked = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val closing =
                    async(Dispatchers.Default) {
                        transactionRunner.inTransaction {
                            val store = checkNotNull(storeRepository.findByIdForUpdate(open))
                            store.close(LocalDate.of(2027, 3, 31))
                            storeRepository.save(store)
                            locked.complete(Unit)
                            release.await()
                        }
                    }
                locked.await()
                val assigning =
                    async(Dispatchers.Default) { runCatching { assignManager.assign(AssignStoreManagerCommand(open, account(1))) } }

                assertNull(withTimeoutOrNull(500) { assigning.await() }, "폐점이 끝나기 전에는 지정이 끝나면 안 됩니다")

                release.complete(Unit)
                closing.await()
                assertTrue(withTimeout(5_000) { assigning.await() }.exceptionOrNull() is StoreClosedException)
            }

            assertTrue(managersOf(open).isEmpty())
        }
    }

    @Nested
    @DisplayName("매니저 해제")
    inner class Unassign {
        @Test
        fun `해제하면 매니저가 아니게 된다`() {
            assign(open, account(1))
            assign(open, account(2))

            unassign(open, account(1))

            assertEquals(listOf(account(2)), managersOf(open))
        }

        @Test
        fun `폐점한 매장의 지정도 해제할 수 있다`() {
            execute("INSERT INTO store_managers (store_id, principal_type, principal_id) VALUES (3, 'PARTNER', '${UUID(0, 1)}')")

            unassign(closed, account(1))

            assertTrue(managersOf(closed).isEmpty())
        }

        @Test
        fun `지정이 없는 계정의 해제는 거부한다`() {
            assertRejected<StoreManagerNotFoundException> { unassign(open, account(1)) }
        }

        @Test
        fun `계정 종류가 다르면 해제되지 않고 다른 종류의 지정은 유지된다`() {
            assign(open, account(1, PrincipalType.PARTNER))

            assertRejected<StoreManagerNotFoundException> { unassign(open, account(1, PrincipalType.EMPLOYEE)) }

            assertEquals(listOf(account(1, PrincipalType.PARTNER)), managersOf(open))
        }

        @Test
        fun `없는 매장은 찾을 수 없다`() {
            assertRejected<StoreNotFoundException> { unassign(StoreId(999), account(1)) }
        }

        @Test
        fun `해제해도 같은 계정의 다른 매장 지정은 유지된다`() {
            assign(open, account(1))
            assign(other, account(1))

            unassign(open, account(1))

            assertEquals(listOf(account(1)), managersOf(other))
        }
    }

    @Nested
    @DisplayName("매니저 조회")
    inner class Get {
        @Test
        fun `매장의 매니저를 지정한 순서로 돌려준다`() {
            assign(open, account(3))
            assign(open, account(1))
            assign(open, account(2))

            assertEquals(listOf(account(3), account(1), account(2)), managersOf(open))
        }

        @Test
        fun `폐점한 매장의 매니저도 조회된다`() {
            execute("INSERT INTO store_managers (store_id, principal_type, principal_id) VALUES (3, 'PARTNER', '${UUID(0, 1)}')")

            assertEquals(listOf(account(1)), managersOf(closed))
        }

        @Test
        fun `없는 매장의 매니저는 조회할 수 없다`() {
            assertRejected<StoreNotFoundException> { call { getManagers.getByStore(StoreId(999)) } }
        }

        @Test
        fun `계정이 매니저인 지정을 폐점한 매장 포함해 지정한 순서로 돌려준다`() {
            assign(open, account(1))
            execute("INSERT INTO store_managers (store_id, principal_type, principal_id) VALUES (3, 'PARTNER', '${UUID(0, 1)}')")
            assign(other, account(9))

            val found = call { getManagersByPrincipal.getByPrincipal(account(1)) }

            assertEquals(setOf(open, closed), found.map { it.storeId }.toSet())
            assertEquals(2, found.size)
        }

        @Test
        fun `지정이 없는 계정은 빈 목록이다`() {
            assertTrue(call { getManagersByPrincipal.getByPrincipal(account(1)) }.isEmpty())
        }
    }

    @Nested
    @DisplayName("내 매장 목록 (내가 매니저인 매장)")
    inner class FindManaged {
        private fun requester(
            principal: PrincipalKey,
            vararg roles: String,
        ) = Requester(principal, roles.toSet())

        private fun managed(requester: Requester) = call { findManagedStores.find(requester) }

        @Test
        fun `지정된 매장의 ID, 코드, 이름, 상태를 돌려준다`() {
            assign(open, account(1))

            val result = managed(requester(account(1))).single()

            assertEquals(open, result.storeId)
            assertEquals("ST-00000001", result.code)
            assertEquals("강남역점", result.name)
            assertEquals(StoreStatusResult.PREPARING, result.status)
        }

        @Test
        fun `폐점한 매장도 포함하고 폐점하지 않은 매장 뒤에 이름순으로 놓는다`() {
            execute(insertStoreSql("ST-00000004", "나루점", "OPEN"))
            execute(insertStoreSql("ST-00000005", "가나폐점", "CLOSED", closingDate = "2027-03-31"))
            listOf(1L, 2L, 3L, 4L, 5L).forEach { execute(insertManagerSql(it, "PARTNER", UUID(0, 1))) }

            val result = managed(requester(account(1)))

            assertEquals(listOf("강남역점", "나루점", "역삼점", "가나폐점", "폐점점"), result.map { it.name })
            assertEquals(StoreStatusResult.CLOSED, result.last().status)
        }

        @Test
        fun `지정이 없으면 빈 목록이다`() {
            assertTrue(managed(requester(account(1))).isEmpty())
        }

        @Test
        fun `다른 계정의 지정은 섞이지 않는다`() {
            assign(open, account(1))
            assign(other, account(2))

            assertEquals(listOf(open), managed(requester(account(1))).map { it.storeId })
        }

        @Test
        fun `계정 ID가 같아도 계정 종류가 다르면 그 지정의 주인이 아니다`() {
            assign(open, account(1, PrincipalType.PARTNER))

            assertTrue(managed(requester(account(1, PrincipalType.EMPLOYEE), StoreRoles.MANAGER)).isEmpty())
        }

        @Test
        fun `본사 직원은 store manager 역할이 있어야 매니저로 인정한다`() {
            assign(open, account(1, PrincipalType.EMPLOYEE))

            assertEquals(1, managed(requester(account(1, PrincipalType.EMPLOYEE), StoreRoles.MANAGER)).size)
            assertTrue(managed(requester(account(1, PrincipalType.EMPLOYEE))).isEmpty())
        }

        @Test
        fun `매장 관리·본사 조회 권한도 모든 매장이 아니라 자기가 지정된 매장만 돌려준다`() {
            assign(open, account(1, PrincipalType.EMPLOYEE))

            val admin = requester(account(1, PrincipalType.EMPLOYEE), StoreRoles.ADMIN, StoreRoles.MANAGER)
            val adminWithoutAssignment = requester(account(2, PrincipalType.EMPLOYEE), StoreRoles.ADMIN, StoreRoles.VIEWER)

            assertEquals(listOf(open), managed(admin).map { it.storeId })
            assertTrue(managed(adminWithoutAssignment).isEmpty())
        }

        @ParameterizedTest
        @EnumSource(PrincipalType::class, names = ["SYSTEM", "CUSTOMER"])
        fun `서비스 계정과 고객은 매니저로 인정하지 않는다`(type: PrincipalType) {
            execute(insertManagerSql(1, "PARTNER", UUID(0, 1)))

            assertTrue(managed(requester(account(1, type), StoreRoles.MANAGER)).isEmpty())
        }
    }
}
