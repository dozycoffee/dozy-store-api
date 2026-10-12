package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.fixture.newStoreManagerAssignment
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.management.application.port.outbound.StoreManagerAssignmentRepository
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerAlreadyAssignedException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("ExposedStoreManagerAssignmentRepository")
class ExposedStoreManagerAssignmentRepositoryTest : ApplicationTest() {
    @Autowired
    private lateinit var repository: StoreManagerAssignmentRepository

    private fun account(
        n: Long,
        type: PrincipalType = PrincipalType.PARTNER,
    ) = principalKey(type, UUID(0, n))

    private fun assign(
        storeId: Long,
        principal: PrincipalKey,
    ) = tx { repository.insert(newStoreManagerAssignment(StoreId(storeId), principal)) }

    @BeforeEach
    fun stores() {
        // 매장 1(영업 준비), 2(영업 준비), 3(폐점)
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "PREPARING"))
        execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    @Nested
    @DisplayName("지정할 때")
    inner class Insert {
        @Test
        fun `지정을 저장하고 지정 시각이 채워진 값을 돌려준다`() {
            val before = Instant.now().minus(1, ChronoUnit.MINUTES)

            val saved = assign(1, account(1))

            assertEquals(StoreId(1), saved.storeId)
            assertEquals(account(1), saved.principal)
            assertTrue(saved.assignedAt.isAfter(before), "지정 시각이 DB 시계로 채워져야 합니다: ${saved.assignedAt}")
            assertEquals(listOf(account(1)), tx { repository.findByStoreId(StoreId(1)) }.map { it.principal })
        }

        @Test
        fun `같은 매장에 같은 계정을 다시 지정하면 거부하고 저장하지 않는다`() {
            assign(1, account(1))

            assertFailsWith<StoreManagerAlreadyAssignedException> { assign(1, account(1)) }

            assertEquals(1, tx { repository.findByStoreId(StoreId(1)) }.size)
        }

        @Test
        fun `계정 ID가 같아도 계정 종류가 다르면 별개의 지정이다`() {
            assign(1, account(1, PrincipalType.PARTNER))
            assign(1, account(1, PrincipalType.EMPLOYEE))

            val types = tx { repository.findByStoreId(StoreId(1)) }.map { it.principal.type }.toSet()

            assertEquals(setOf(PrincipalType.PARTNER, PrincipalType.EMPLOYEE), types)
        }

        @Test
        fun `한 계정은 여러 매장의 매니저가 되고 한 매장에는 여러 계정이 있을 수 있다`() {
            assign(1, account(1))
            assign(2, account(1))
            assign(1, account(2))

            assertEquals(2, tx { repository.findByStoreId(StoreId(1)) }.size)
            assertEquals(2, tx { repository.findByPrincipal(account(1)) }.size)
        }

        @Test
        fun `폐점한 매장의 지정도 저장소에는 막지 않는다 (폐점 확인은 서비스 규칙이다)`() {
            val saved = assign(3, account(1))

            assertEquals(StoreId(3), saved.storeId)
        }

        @Test
        fun `없는 매장에는 지정할 수 없다`() {
            val e = assertFailsWith<Exception> { assign(999, account(1)) }

            assertTrue(e !is StoreManagerAlreadyAssignedException)
        }

        @Test
        fun `서비스 계정과 고객 종류는 DB가 거부한다`() {
            listOf("SYSTEM", "CUSTOMER").forEach { type ->
                assertFailsWith<Exception> {
                    execute("INSERT INTO store_managers (store_id, principal_type, principal_id) VALUES (1, '$type', '${UUID(0, 9)}')")
                }
            }
        }

        @Test
        fun `같은 쌍을 동시에 지정하면 하나만 성공한다`() {
            val attempts = 8

            val results =
                runBlocking {
                    (1..attempts)
                        .map {
                            async(Dispatchers.Default) {
                                runCatching {
                                    transactionRunner.inTransaction { repository.insert(newStoreManagerAssignment(StoreId(1), account(1))) }
                                }
                            }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is StoreManagerAlreadyAssignedException })
            assertEquals(1, tx { repository.findByStoreId(StoreId(1)) }.size)
        }
    }

    @Nested
    @DisplayName("해제할 때")
    inner class Delete {
        @Test
        fun `지정을 지우고 지웠다고 알려 준다`() {
            assign(1, account(1))

            assertTrue(tx { repository.delete(StoreId(1), account(1)) })

            assertTrue(tx { repository.findByStoreId(StoreId(1)) }.isEmpty())
        }

        @Test
        fun `지울 지정이 없으면 false를 돌려준다`() {
            assertFalse(tx { repository.delete(StoreId(1), account(1)) })
        }

        @Test
        fun `계정 종류만 다른 같은 ID의 지정은 지워지지 않는다`() {
            assign(1, account(1, PrincipalType.PARTNER))
            assign(1, account(1, PrincipalType.EMPLOYEE))

            assertTrue(tx { repository.delete(StoreId(1), account(1, PrincipalType.PARTNER)) })

            assertEquals(listOf(PrincipalType.EMPLOYEE), tx { repository.findByStoreId(StoreId(1)) }.map { it.principal.type })
        }

        @Test
        fun `다른 매장의 같은 계정 지정은 지워지지 않는다`() {
            assign(1, account(1))
            assign(2, account(1))

            tx { repository.delete(StoreId(1), account(1)) }

            assertEquals(listOf(StoreId(2)), tx { repository.findByPrincipal(account(1)) }.map { it.storeId })
        }

        @Test
        fun `폐점한 매장의 지정도 해제할 수 있다`() {
            assign(3, account(1))

            assertTrue(tx { repository.delete(StoreId(3), account(1)) })
        }
    }

    @Nested
    @DisplayName("조회할 때")
    inner class Find {
        @Test
        fun `매장의 지정은 지정한 순서로 돌려준다`() {
            assign(1, account(3))
            assign(1, account(1))
            assign(1, account(2))

            val principals = tx { repository.findByStoreId(StoreId(1)) }.map { it.principal }

            assertEquals(listOf(account(3), account(1), account(2)), principals)
        }

        @Test
        fun `지정이 없으면 빈 목록이다`() {
            assertTrue(tx { repository.findByStoreId(StoreId(1)) }.isEmpty())
            assertTrue(tx { repository.findByPrincipal(account(1)) }.isEmpty())
        }

        @Test
        fun `다른 매장의 지정은 섞이지 않는다`() {
            assign(1, account(1))
            assign(2, account(2))

            assertEquals(listOf(account(1)), tx { repository.findByStoreId(StoreId(1)) }.map { it.principal })
        }

        @Test
        fun `계정이 매니저인 지정은 폐점한 매장도 포함해 지정한 순서로 돌려준다`() {
            assign(3, account(1))
            assign(1, account(1))
            assign(2, account(9))

            val found = tx { repository.findByPrincipal(account(1)) }

            assertEquals(listOf(StoreId(3), StoreId(1)), found.map { it.storeId })
            found.forEach { assertNotNull(it.assignedAt) }
        }

        @Test
        fun `계정 종류가 다르면 같은 ID라도 찾지 않는다`() {
            assign(1, account(1, PrincipalType.EMPLOYEE))

            assertTrue(tx { repository.findByPrincipal(account(1, PrincipalType.PARTNER)) }.isEmpty())
        }
    }
}
