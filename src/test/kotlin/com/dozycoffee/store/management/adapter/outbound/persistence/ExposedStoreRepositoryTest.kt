package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.newStore
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.store.StoreCode
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ExposedStoreRepository")
class ExposedStoreRepositoryTest : ApplicationTest() {
    @Autowired
    private lateinit var repository: StoreRepository

    /** 검증 대상이 아닌 행(폐점 매장 등)이나 제약 위반을 SQL로 직접 만든다. */
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
    @DisplayName("등록하고 조회할 때")
    inner class InsertAndFind {
        @Test
        fun `저장한 매장을 ID와 버전이 채워진 채로 돌려준다`() {
            val saved = tx { repository.insert(newStore(code = StoreCode.create(7))) }

            assertEquals(StoreId(1), saved.id)
            assertEquals(StoreCode.create(7), saved.code)
            assertEquals(StoreStatus.PREPARING, saved.status)
            assertNull(saved.closingDate)
            assertEquals(0, saved.version)
        }

        @Test
        fun `저장한 값을 그대로 조회한다`() {
            val newStore =
                newStore(
                    name = "강남역점",
                    address = address(detailAddress = null, sido = "서울특별시", sigungu = "강남구"),
                    phone = "02-1234-5678",
                    openingDate = LocalDate.of(2026, 11, 1),
                )

            val saved = tx { repository.insert(newStore) }
            val found = tx { repository.findById(saved.id) }

            assertNotNull(found)
            assertEquals("강남역점", found.name)
            assertEquals(newStore.address, found.address)
            assertNull(found.address.detailAddress)
            assertEquals("02-1234-5678", found.phone)
            assertEquals(LocalDate.of(2026, 11, 1), found.openingDate)
            assertEquals(saved.code, found.code)
            assertEquals(0, found.version)
        }

        @Test
        fun `없는 매장은 null을 돌려준다`() {
            assertNull(tx { repository.findById(StoreId(999)) })
        }

        @Test
        fun `폐점한 매장도 조회된다`() {
            execute(insertStoreSql("ST-00000001", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            val found = tx { repository.findById(StoreId(1)) }

            assertNotNull(found)
            assertEquals(StoreStatus.CLOSED, found.status)
            assertEquals(LocalDate.of(2027, 3, 31), found.closingDate)
        }
    }

    @Nested
    @DisplayName("매장명이 같을 때")
    inner class NameUniqueness {
        @Test
        fun `폐점하지 않은 매장과 이름이 같으면 거부하고 저장하지 않는다`() {
            tx { repository.insert(newStore(code = StoreCode.create(1), name = "강남역점")) }

            assertFailsWith<StoreNameDuplicatedException> {
                tx { repository.insert(newStore(code = StoreCode.create(2), name = "강남역점")) }
            }

            assertNull(tx { repository.findById(StoreId(2)) })
        }

        @ParameterizedTest
        @EnumSource(StoreStatus::class, names = ["CLOSED"], mode = EnumSource.Mode.EXCLUDE)
        fun `폐점하지 않은 어느 상태의 매장과도 이름이 겹치면 거부한다`(status: StoreStatus) {
            execute(insertStoreSql("ST-00000001", "강남역점", status.name))

            assertFailsWith<StoreNameDuplicatedException> {
                tx { repository.insert(newStore(code = StoreCode.create(2), name = "강남역점")) }
            }
        }

        @Test
        fun `폐점한 매장의 이름은 새 매장에 다시 쓸 수 있다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = "2027-03-31"))

            val saved = tx { repository.insert(newStore(code = StoreCode.create(2), name = "강남역점")) }

            assertEquals("강남역점", saved.name)
            assertEquals(StoreStatus.PREPARING, saved.status)
        }

        @Test
        fun `이름이 같은 폐점 매장이 여럿이어도 새 매장은 하나만 등록된다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = "2026-12-31"))
            execute(insertStoreSql("ST-00000002", "강남역점", "CLOSED", closingDate = "2027-03-31"))

            tx { repository.insert(newStore(code = StoreCode.create(3), name = "강남역점")) }

            assertFailsWith<StoreNameDuplicatedException> {
                tx { repository.insert(newStore(code = StoreCode.create(4), name = "강남역점")) }
            }
        }

        @Test
        fun `같은 이름으로 동시에 등록하면 하나만 성공한다`() {
            val attempts = 8

            val results =
                runBlocking {
                    (1..attempts)
                        .map { n ->
                            async(Dispatchers.Default) {
                                runCatching {
                                    transactionRunner.inTransaction {
                                        repository.insert(newStore(code = StoreCode.create(n.toLong()), name = "동시등록점"))
                                    }
                                }
                            }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is StoreNameDuplicatedException })
        }
    }

    @Nested
    @DisplayName("폐점하지 않은 같은 이름이 있는지 확인할 때")
    inner class ExistsActiveByName {
        @Test
        fun `폐점하지 않은 매장이 있으면 true다`() {
            tx { repository.insert(newStore(name = "강남역점")) }

            assertTrue(tx { repository.existsActiveByName("강남역점") })
        }

        @Test
        fun `폐점한 매장뿐이면 false다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = "2027-03-31"))

            assertFalse(tx { repository.existsActiveByName("강남역점") })
        }

        @Test
        fun `이름이 다르면 false다`() {
            tx { repository.insert(newStore(name = "강남역점")) }

            assertFalse(tx { repository.existsActiveByName("역삼점") })
        }
    }

    @Nested
    @DisplayName("DB 제약")
    inner class Constraints {
        @Test
        fun `매장 코드는 중복될 수 없다`() {
            tx { repository.insert(newStore(code = StoreCode.create(1), name = "강남역점")) }

            val e =
                assertFailsWith<Exception> {
                    tx { repository.insert(newStore(code = StoreCode.create(1), name = "역삼점")) }
                }

            assertTrue(e !is StoreNameDuplicatedException)
            assertTrue(e.isUniqueViolationOf("stores_code_key"))
        }

        @Test
        fun `허용되지 않은 상태값은 저장할 수 없다`() {
            assertFailsWith<Exception> { execute(insertStoreSql("ST-00000001", "강남역점", "UNKNOWN")) }
        }

        @Test
        fun `폐점한 매장에 폐점일이 없으면 저장할 수 없다`() {
            assertFailsWith<Exception> { execute(insertStoreSql("ST-00000001", "강남역점", "CLOSED", closingDate = null)) }
        }

        @Test
        fun `폐점하지 않은 매장에 폐점일이 있으면 저장할 수 없다`() {
            assertFailsWith<Exception> { execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", closingDate = "2027-03-31")) }
        }
    }
}
