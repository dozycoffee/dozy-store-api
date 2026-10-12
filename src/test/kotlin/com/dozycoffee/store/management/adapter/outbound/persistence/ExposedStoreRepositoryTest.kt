package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.common.paging.PageRequest
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.core.VersionConflictException
import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.fixture.newStore
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.application.port.outbound.StoreSearchCriteria
import com.dozycoffee.store.management.domain.store.Store
import com.dozycoffee.store.management.domain.store.StoreCode
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.r2dbc.selectAll
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

    /** `Store`는 엔티티라 값 동등성이 없다. 저장된 모든 값이 같은지 필드로 비교한다. */
    private fun assertSameStore(
        expected: Store,
        actual: Store?,
    ) {
        assertNotNull(actual)
        assertEquals(expected.id, actual.id)
        assertEquals(expected.code, actual.code)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.address, actual.address)
        assertEquals(expected.phone, actual.phone)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.openingDate, actual.openingDate)
        assertEquals(expected.closingDate, actual.closingDate)
        assertEquals(expected.version, actual.version)
    }

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

    @Nested
    @DisplayName("잠그고 조회할 때")
    inner class FindForUpdate {
        @Test
        fun `매장을 조회하고 없으면 null을 돌려준다`() {
            val saved = tx { repository.insert(newStore()) }

            assertSameStore(saved, tx { repository.findByIdForUpdate(saved.id) })
            assertNull(tx { repository.findByIdForUpdate(StoreId(999)) })
        }

        @Test
        fun `행을 잠근 트랜잭션이 끝날 때까지 다른 트랜잭션의 잠금 조회는 기다린다`() {
            val saved = tx { repository.insert(newStore()) }

            runBlocking {
                val locked = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val holder =
                    async(Dispatchers.Default) {
                        transactionRunner.inTransaction {
                            repository.findByIdForUpdate(saved.id)
                            locked.complete(Unit)
                            release.await()
                        }
                    }
                locked.await()
                val waiter = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForUpdate(saved.id) } }

                assertNull(withTimeoutOrNull(500) { waiter.await() }, "잠금이 풀리기 전에는 조회가 끝나면 안 됩니다")

                release.complete(Unit)
                holder.await()
                assertNotNull(withTimeout(5_000) { waiter.await() })
            }
        }
    }

    @Nested
    @DisplayName("저장할 때")
    inner class Save {
        private fun registered() = tx { repository.insert(newStore(code = StoreCode.create(1), name = "강남역점")) }

        @Test
        fun `바뀐 정보를 저장하고 버전을 1 올려 돌려준다`() {
            val store = registered()
            store.update("서초점", address(detailAddress = null, sigungu = "서초구"), "02-9999-0000", LocalDate.of(2026, 12, 1))

            val saved = tx { repository.save(store) }

            assertEquals(1, saved.version)
            assertSameStore(saved, tx { repository.findById(store.id) })
            assertEquals("서초점", saved.name)
            assertEquals("서초구", saved.address.sigungu)
            assertNull(saved.address.detailAddress)
            assertEquals("02-9999-0000", saved.phone)
            assertEquals(LocalDate.of(2026, 12, 1), saved.openingDate)
            assertEquals(store.code, saved.code)
        }

        @Test
        fun `저장할 때마다 버전이 1씩 오른다`() {
            var store = registered()

            repeat(3) { store = tx { repository.save(store) } }

            assertEquals(3, store.version)
        }

        @Test
        fun `개점하면 상태와 개점일이 저장된다`() {
            val store = registered()
            store.open(LocalDate.of(2026, 12, 25))

            val saved = tx { repository.save(store) }

            assertEquals(StoreStatus.OPEN, saved.status)
            assertEquals(LocalDate.of(2026, 12, 25), saved.openingDate)
            assertNull(saved.closingDate)
        }

        @Test
        fun `폐점하면 상태와 폐점일이 저장된다`() {
            val store = registered()
            store.close(LocalDate.of(2027, 3, 31))

            val saved = tx { repository.save(store) }

            assertEquals(StoreStatus.CLOSED, saved.status)
            assertEquals(LocalDate.of(2027, 3, 31), saved.closingDate)
        }

        @Test
        fun `저장하면 수정 시각이 DB 시계로 갱신된다`() {
            val store = registered()
            val before = tx { StoresTable.selectAll().single()[StoresTable.updatedAt] }

            tx { repository.save(store) }

            val after = tx { StoresTable.selectAll().single()[StoresTable.updatedAt] }
            assertTrue(after.isAfter(before), "수정 시각이 갱신되지 않았습니다: $before → $after")
        }

        @Test
        fun `그 사이 다른 변경이 반영됐으면 거부하고 현재 버전을 알려 준다`() {
            val first = registered()
            val second = checkNotNull(tx { repository.findById(first.id) })
            first.update("서초점", address(), "02-1111-1111", LocalDate.of(2026, 11, 1))
            tx { repository.save(first) }
            second.update("역삼점", address(), "02-2222-2222", LocalDate.of(2026, 11, 1))

            val e = assertFailsWith<VersionConflictException> { tx { repository.save(second) } }

            assertEquals(1, e.currentVersion)
            val stored = checkNotNull(tx { repository.findById(first.id) })
            assertEquals("서초점", stored.name)
            assertEquals("02-1111-1111", stored.phone)
            assertEquals(1, stored.version)
        }

        @Test
        fun `같은 버전을 동시에 저장하면 하나만 성공한다`() {
            val saved = registered()
            val attempts = 6
            val stores =
                (1..attempts).map { n ->
                    checkNotNull(tx { repository.findById(saved.id) }).also {
                        it.update("지점$n", address(), "02-0000-000$n", LocalDate.of(2026, 11, 1))
                    }
                }

            val results =
                runBlocking {
                    stores
                        .map { store ->
                            async(Dispatchers.Default) { runCatching { transactionRunner.inTransaction { repository.save(store) } } }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(attempts - 1, results.count { it.exceptionOrNull() is VersionConflictException })
            assertEquals(1, checkNotNull(tx { repository.findById(saved.id) }).version)
        }

        @Test
        fun `폐점하지 않은 다른 매장의 이름으로 바꾸면 거부하고 저장하지 않는다`() {
            tx { repository.insert(newStore(code = StoreCode.create(1), name = "강남역점")) }
            val other = tx { repository.insert(newStore(code = StoreCode.create(2), name = "역삼점")) }
            other.update("강남역점", address(), "02-1234-5678", LocalDate.of(2026, 11, 1))

            assertFailsWith<StoreNameDuplicatedException> { tx { repository.save(other) } }

            val stored = checkNotNull(tx { repository.findById(other.id) })
            assertEquals("역삼점", stored.name)
            assertEquals(0, stored.version)
        }

        @Test
        fun `이름을 그대로 두고 다른 정보만 바꾸는 것은 자기 자신과 겹치지 않는다`() {
            val store = registered()
            store.update("강남역점", address(), "02-9999-0000", LocalDate.of(2026, 11, 1))

            val saved = tx { repository.save(store) }

            assertEquals("강남역점", saved.name)
            assertEquals("02-9999-0000", saved.phone)
        }

        @Test
        fun `폐점한 매장의 이름으로는 바꿀 수 있다`() {
            execute(insertStoreSql("ST-00000009", "옛점", "CLOSED", closingDate = "2027-03-31"))
            val store = registered()
            store.update("옛점", address(), "02-1234-5678", LocalDate.of(2026, 11, 1))

            assertEquals("옛점", tx { repository.save(store) }.name)
        }
    }

    @Nested
    @DisplayName("같은 이름을 확인할 때 자기 자신을 제외하면")
    inner class ExistsActiveByNameExcluding {
        @Test
        fun `그 매장뿐이면 false다`() {
            val store = tx { repository.insert(newStore(name = "강남역점")) }

            assertFalse(tx { repository.existsActiveByName("강남역점", excluding = store.id) })
        }

        @Test
        fun `다른 매장이 같은 이름이면 true다`() {
            tx { repository.insert(newStore(code = StoreCode.create(1), name = "강남역점")) }
            val other = tx { repository.insert(newStore(code = StoreCode.create(2), name = "역삼점")) }

            assertTrue(tx { repository.existsActiveByName("강남역점", excluding = other.id) })
        }
    }

    @Nested
    @DisplayName("ID 목록으로 조회할 때")
    inner class FindAllByIds {
        private fun names(vararg ids: Long) = tx { repository.findAllByIds(ids.map { StoreId(it) }) }.map { it.name }

        @Test
        fun `요청한 매장만 돌려주고 없는 ID는 빠진다`() {
            execute(insertStoreSql("ST-00000001", "강남점"))
            execute(insertStoreSql("ST-00000002", "나루점"))
            execute(insertStoreSql("ST-00000003", "사당점"))

            assertEquals(listOf("강남점", "사당점"), names(1, 3, 999))
        }

        @Test
        fun `1000개의 ID도 한 번에 조회한다`() {
            execute(insertStoreSql("ST-00000001", "강남점"))
            execute(insertStoreSql("ST-00000002", "나루점"))

            val found = tx { repository.findAllByIds((1L..1000L).map { StoreId(it) }) }

            assertEquals(listOf("강남점", "나루점"), found.map { it.name })
        }

        @Test
        fun `빈 목록이면 빈 목록을 돌려준다`() {
            execute(insertStoreSql("ST-00000001", "강남점"))

            assertTrue(tx { repository.findAllByIds(emptyList()) }.isEmpty())
        }

        @Test
        fun `매장명 가나다순으로 정렬한다`() {
            execute(insertStoreSql("ST-00000001", "사당점"))
            execute(insertStoreSql("ST-00000002", "강남점"))
            execute(insertStoreSql("ST-00000003", "나루점"))

            assertEquals(listOf("강남점", "나루점", "사당점"), names(1, 2, 3))
        }

        @Test
        fun `영문은 대소문자와 관계없이 사전순으로 정렬한다 (한국어 ICU 정렬 규칙)`() {
            execute(insertStoreSql("ST-00000001", "charlie점"))
            execute(insertStoreSql("ST-00000002", "Bravo점"))
            execute(insertStoreSql("ST-00000003", "alpha점"))

            assertEquals(listOf("alpha점", "Bravo점", "charlie점"), names(1, 2, 3))
        }

        @Test
        fun `폐점한 매장은 이름이 앞서도 폐점하지 않은 매장 뒤로 가고 폐점 매장끼리는 이름순이다`() {
            execute(insertStoreSql("ST-00000001", "가나점", "CLOSED", closingDate = "2027-03-31"))
            execute(insertStoreSql("ST-00000002", "사당점", "OPEN"))
            execute(insertStoreSql("ST-00000003", "가가점", "CLOSED", closingDate = "2027-03-31"))
            execute(insertStoreSql("ST-00000004", "강남점", "SUSPENDED"))

            assertEquals(listOf("강남점", "사당점", "가가점", "가나점"), names(1, 2, 3, 4))
        }

        @Test
        fun `저장된 값을 그대로 돌려준다`() {
            execute(insertStoreSql("ST-00000001", "폐점점", "CLOSED", closingDate = "2027-03-31"))

            val store = tx { repository.findAllByIds(listOf(StoreId(1))) }.single()

            assertEquals(StoreId(1), store.id)
            assertEquals(StoreCode("ST-00000001"), store.code)
            assertEquals(StoreStatus.CLOSED, store.status)
            assertEquals(LocalDate.of(2027, 3, 31), store.closingDate)
        }
    }

    @Nested
    @DisplayName("조건으로 검색할 때")
    inner class Search {
        /** 1 강남역점(서울·강남, 영업), 2 역삼점(서울·강남, 준비), 3 서초점(서울·서초, 휴업), 4 해운대점(부산·해운대, 영업), 5 옛점(서울·강남, 폐점) */
        private fun seed() {
            execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", sido = "서울특별시", sigungu = "강남구"))
            execute(insertStoreSql("ST-00000002", "역삼점", "PREPARING", sido = "서울특별시", sigungu = "강남구"))
            execute(insertStoreSql("ST-00000003", "서초점", "SUSPENDED", sido = "서울특별시", sigungu = "서초구"))
            execute(insertStoreSql("ST-00000004", "해운대점", "OPEN", sido = "부산광역시", sigungu = "해운대구"))
            execute(insertStoreSql("ST-00000005", "옛점", "CLOSED", closingDate = "2027-03-31", sido = "서울특별시", sigungu = "강남구"))
        }

        private fun search(criteria: StoreSearchCriteria = StoreSearchCriteria()) = tx { repository.search(criteria) }

        private fun names(criteria: StoreSearchCriteria = StoreSearchCriteria()) = search(criteria).content.map { it.name }

        @Test
        fun `조건이 없으면 폐점을 뺀 모든 매장을 이름순으로 돌려준다`() {
            seed()

            val page = search()

            assertEquals(listOf("강남역점", "서초점", "역삼점", "해운대점"), page.content.map { it.name })
            assertEquals(4, page.totalElements)
            assertEquals(1, page.totalPages)
        }

        @Test
        fun `상태를 지정하면 그 상태의 매장만 나온다`() {
            seed()

            assertEquals(listOf("강남역점", "해운대점"), names(StoreSearchCriteria(statuses = setOf(StoreStatus.OPEN))))
            assertEquals(
                listOf("서초점", "역삼점"),
                names(StoreSearchCriteria(statuses = setOf(StoreStatus.PREPARING, StoreStatus.SUSPENDED))),
            )
        }

        @Test
        fun `상태로 폐점을 지정해야 폐점한 매장이 나온다`() {
            seed()

            assertEquals(listOf("옛점"), names(StoreSearchCriteria(statuses = setOf(StoreStatus.CLOSED))))
            assertEquals(
                listOf("강남역점", "옛점", "해운대점"),
                names(StoreSearchCriteria(statuses = setOf(StoreStatus.CLOSED, StoreStatus.OPEN))),
            )
        }

        @Test
        fun `시·도와 시·군·구로 거른다`() {
            seed()

            assertEquals(listOf("강남역점", "서초점", "역삼점"), names(StoreSearchCriteria(sido = "서울특별시")))
            assertEquals(listOf("강남역점", "역삼점"), names(StoreSearchCriteria(sido = "서울특별시", sigungu = "강남구")))
            assertEquals(listOf("해운대점"), names(StoreSearchCriteria(sido = "부산광역시")))
        }

        @Test
        fun `시·군·구만 지정해도 거른다`() {
            seed()

            assertEquals(listOf("서초점"), names(StoreSearchCriteria(sigungu = "서초구")))
        }

        @Test
        fun `매장명은 부분 일치로 찾는다`() {
            seed()

            assertEquals(listOf("강남역점", "역삼점"), names(StoreSearchCriteria(nameContains = "역")))
            assertEquals(listOf("해운대점"), names(StoreSearchCriteria(nameContains = "해운")))
            assertTrue(names(StoreSearchCriteria(nameContains = "없는이름")).isEmpty())
        }

        @Test
        fun `매장명 검색은 대소문자를 무시한다`() {
            execute(insertStoreSql("ST-00000001", "Dozy Gangnam"))
            execute(insertStoreSql("ST-00000002", "dozy Seocho"))
            execute(insertStoreSql("ST-00000003", "Other"))

            assertEquals(listOf("Dozy Gangnam", "dozy Seocho"), names(StoreSearchCriteria(nameContains = "DOZY")))
            assertEquals(listOf("Dozy Gangnam", "dozy Seocho"), names(StoreSearchCriteria(nameContains = "dozy")))
        }

        @Test
        fun `매장명의 퍼센트와 밑줄과 역슬래시는 글자 그대로 찾는다`() {
            execute(insertStoreSql("ST-00000001", "100%점"))
            execute(insertStoreSql("ST-00000002", "100점"))
            execute(insertStoreSql("ST-00000003", "a_b점"))
            execute(insertStoreSql("ST-00000004", "axb점"))
            execute(insertStoreSql("ST-00000005", "역\\삼점"))
            execute(insertStoreSql("ST-00000006", "역삼점"))

            assertEquals(listOf("100%점"), names(StoreSearchCriteria(nameContains = "%")))
            assertEquals(listOf("a_b점"), names(StoreSearchCriteria(nameContains = "_")))
            assertEquals(listOf("역\\삼점"), names(StoreSearchCriteria(nameContains = "\\")))
        }

        @Test
        fun `ID로 거르고 없는 ID는 빠진다`() {
            seed()

            assertEquals(
                listOf("강남역점", "해운대점"),
                names(StoreSearchCriteria(ids = setOf(StoreId(1), StoreId(4), StoreId(999)))),
            )
        }

        @Test
        fun `조건은 모두 만족해야 한다`() {
            seed()

            val criteria =
                StoreSearchCriteria(
                    ids = setOf(StoreId(1), StoreId(2), StoreId(4)),
                    statuses = setOf(StoreStatus.OPEN),
                    sido = "서울특별시",
                    nameContains = "역",
                )

            assertEquals(listOf("강남역점"), names(criteria))
        }

        @Test
        fun `같은 이름이면 ID순이다 (폐점한 매장은 이름이 같을 수 있다)`() {
            execute(insertStoreSql("ST-00000001", "같은점", "CLOSED", closingDate = "2027-01-01"))
            execute(insertStoreSql("ST-00000002", "같은점", "CLOSED", closingDate = "2027-02-01"))
            execute(insertStoreSql("ST-00000003", "같은점", "CLOSED", closingDate = "2027-03-01"))

            val ids = search(StoreSearchCriteria(statuses = setOf(StoreStatus.CLOSED))).content.map { it.id.value }

            assertEquals(listOf(1L, 2L, 3L), ids)
        }

        @Test
        fun `조건에 맞는 매장이 없으면 빈 페이지다`() {
            seed()

            val page = search(StoreSearchCriteria(sido = "제주특별자치도"))

            assertTrue(page.content.isEmpty())
            assertEquals(0, page.totalElements)
            assertEquals(0, page.totalPages)
        }

        @Test
        fun `나눠서 조회하고 전체 건수와 전체 페이지 수를 알려 준다`() {
            (1..25).forEach { n -> execute(insertStoreSql("ST-%08d".format(n), "지점%02d".format(n))) }

            val first = search(StoreSearchCriteria(page = PageRequest(page = 0, size = 10)))
            val second = search(StoreSearchCriteria(page = PageRequest(page = 1, size = 10)))
            val last = search(StoreSearchCriteria(page = PageRequest(page = 2, size = 10)))

            assertEquals((1..10).map { "지점%02d".format(it) }, first.content.map { it.name })
            assertEquals((11..20).map { "지점%02d".format(it) }, second.content.map { it.name })
            assertEquals((21..25).map { "지점%02d".format(it) }, last.content.map { it.name })
            listOf(first, second, last).forEach {
                assertEquals(25, it.totalElements)
                assertEquals(3, it.totalPages)
                assertEquals(10, it.size)
            }
            assertEquals(2, last.page)
        }

        @Test
        fun `범위를 벗어난 페이지는 빈 목록이고 전체 건수는 그대로다`() {
            (1..5).forEach { n -> execute(insertStoreSql("ST-%08d".format(n), "지점%02d".format(n))) }

            val page = search(StoreSearchCriteria(page = PageRequest(page = 9, size = 10)))

            assertTrue(page.content.isEmpty())
            assertEquals(5, page.totalElements)
            assertEquals(1, page.totalPages)
        }

        @Test
        fun `저장된 값을 그대로 돌려준다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", sido = "서울특별시", sigungu = "강남구"))

            val store = search().content.single()

            assertEquals(StoreId(1), store.id)
            assertEquals(StoreCode("ST-00000001"), store.code)
            assertEquals(StoreStatus.OPEN, store.status)
            assertEquals("강남구", store.address.sigungu)
            assertEquals(0, store.version)
        }
    }
}
