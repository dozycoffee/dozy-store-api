package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("StoreAdminController 조회")
class StoreAdminControllerReadTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    private fun token(
        type: PrincipalType = PrincipalType.EMPLOYEE,
        vararg roles: String,
    ) = tokens.issue(type = type, roles = roles.toList())

    private fun viewer() = token(PrincipalType.EMPLOYEE, "store:viewer")

    private fun get(
        uri: String,
        token: String? = viewer(),
    ): WebTestClient.ResponseSpec {
        val request = webTestClient.get().uri("/api/v1/admin/stores$uri")
        return (if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request).exchange()
    }

    private fun WebTestClient.ResponseSpec.assertInvalid(field: String) {
        expectStatus()
            .isBadRequest
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("INVALID_REQUEST")
            .jsonPath("$.detail")
            .isEqualTo("요청 값이 올바르지 않습니다: $field")
    }

    /** 1 강남역점(서울·강남, 영업), 2 역삼점(서울·강남, 준비), 3 서초점(서울·서초, 휴업), 4 해운대점(부산·해운대, 영업), 5 옛점(서울·강남, 폐점) */
    private fun seed() {
        execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", sido = "서울특별시", sigungu = "강남구"))
        execute(insertStoreSql("ST-00000002", "역삼점", "PREPARING", sido = "서울특별시", sigungu = "강남구"))
        execute(insertStoreSql("ST-00000003", "서초점", "SUSPENDED", sido = "서울특별시", sigungu = "서초구"))
        execute(insertStoreSql("ST-00000004", "해운대점", "OPEN", sido = "부산광역시", sigungu = "해운대구"))
        execute(insertStoreSql("ST-00000005", "옛점", "CLOSED", closingDate = "2027-03-31", sido = "서울특별시", sigungu = "강남구"))
    }

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/stores/{storeId} — 매장 조회")
    inner class GetOne {
        @Test
        fun `매장을 200과 ETag로 돌려준다`() {
            execute(insertStoreSql("ST-00000001", "강남역점", "OPEN", sido = "서울특별시", sigungu = "강남구"))
            execute("UPDATE stores SET version = 3 WHERE id = 1")

            val result =
                get("/1")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.storeId")
                    .isEqualTo(1)
                    .jsonPath("$.code")
                    .isEqualTo("ST-00000001")
                    .jsonPath("$.name")
                    .isEqualTo("강남역점")
                    .jsonPath("$.address.roadAddress")
                    .isEqualTo("도로명")
                    .jsonPath("$.address.detailAddress")
                    .isEqualTo(null)
                    .jsonPath("$.address.sigungu")
                    .isEqualTo("강남구")
                    .jsonPath("$.phone")
                    .isEqualTo("02-1234-5678")
                    .jsonPath("$.status")
                    .isEqualTo("OPEN")
                    .jsonPath("$.openingDate")
                    .isEqualTo("2026-11-01")
                    .jsonPath("$.closingDate")
                    .isEqualTo(null)
                    .jsonPath("$.version")
                    .isEqualTo(3)
                    .returnResult()

            assertEquals("\"3\"", result.responseHeaders.eTag)
        }

        @Test
        fun `폐점한 매장도 조회된다`() {
            execute(insertStoreSql("ST-00000001", "옛점", "CLOSED", closingDate = "2027-03-31"))

            get("/1")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CLOSED")
                .jsonPath("$.closingDate")
                .isEqualTo("2027-03-31")
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            get("/999")
                .expectStatus()
                .isNotFound
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("STORE_NOT_FOUND")
        }

        @ParameterizedTest
        @ValueSource(strings = ["abc", "0", "-1", "1.5"])
        fun `매장 ID가 양의 정수가 아니면 400 INVALID_REQUEST다`(value: String) {
            get("/$value").assertInvalid("storeId")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/stores — 매장 목록")
    inner class Search {
        private fun names(uri: String): List<String> {
            val body =
                get(uri)
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseBody!!
            return JsonMapper()
                .readTree(body)
                .get("content")
                .values()
                .map { it.get("name").asString() }
        }

        @Test
        fun `조건이 없으면 폐점을 뺀 모든 매장을 이름순으로 돌려준다`() {
            seed()

            get("")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(4)
                .jsonPath("$.content[0].name")
                .isEqualTo("강남역점")
                .jsonPath("$.content[3].name")
                .isEqualTo("해운대점")
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(20)
                .jsonPath("$.totalElements")
                .isEqualTo(4)
                .jsonPath("$.totalPages")
                .isEqualTo(1)
        }

        @Test
        fun `목록의 매장은 단건과 같은 모양이고 version을 담는다`() {
            seed()

            get("?q=강남역")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[0].storeId")
                .isEqualTo(1)
                .jsonPath("$.content[0].code")
                .isEqualTo("ST-00000001")
                .jsonPath("$.content[0].address.sido")
                .isEqualTo("서울특별시")
                .jsonPath("$.content[0].status")
                .isEqualTo("OPEN")
                .jsonPath("$.content[0].closingDate")
                .isEqualTo(null)
                .jsonPath("$.content[0].version")
                .isEqualTo(0)
        }

        @Test
        fun `목록 응답에는 ETag를 붙이지 않는다`() {
            seed()

            val headers =
                get("")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseHeaders

            assertNull(headers.eTag)
        }

        @Test
        fun `상태로 거른다`() {
            seed()

            assertEquals(listOf("강남역점", "해운대점"), names("?status=OPEN"))
            assertEquals(listOf("서초점", "역삼점"), names("?status=PREPARING,SUSPENDED"))
            assertEquals(listOf("옛점"), names("?status=CLOSED"))
            assertEquals(listOf("강남역점", "옛점", "해운대점"), names("?status=OPEN,CLOSED"))
        }

        @Test
        fun `지역으로 거른다`() {
            seed()

            assertEquals(listOf("강남역점", "서초점", "역삼점"), names("?sido=서울특별시"))
            assertEquals(listOf("강남역점", "역삼점"), names("?sido=서울특별시&sigungu=강남구"))
        }

        @Test
        fun `매장명으로 찾고 앞뒤 공백은 무시한다`() {
            seed()

            assertEquals(listOf("강남역점", "역삼점"), names("?q=역"))
            assertEquals(listOf("해운대점"), names("?q=+해운+"))
        }

        @Test
        fun `비어 있는 문자열 조건은 거르지 않는다`() {
            seed()

            assertEquals(listOf("강남역점", "서초점", "역삼점", "해운대점"), names("?q=+&sido=&sigungu=+"))
        }

        @Test
        fun `ID로 거르고 없는 ID는 빠진다`() {
            seed()

            assertEquals(listOf("강남역점", "해운대점"), names("?ids=1,4,999"))
        }

        @Test
        fun `조건은 모두 만족해야 한다`() {
            seed()

            assertEquals(listOf("강남역점"), names("?ids=1,2,4&status=OPEN&sido=서울특별시&q=역"))
        }

        @Test
        fun `조건에 맞는 매장이 없으면 빈 목록이다`() {
            seed()

            get("?sido=제주특별자치도")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(0)
                .jsonPath("$.totalElements")
                .isEqualTo(0)
                .jsonPath("$.totalPages")
                .isEqualTo(0)
        }

        @Test
        fun `나눠서 조회하고 전체 건수와 페이지 수를 알려 준다`() {
            (1..25).forEach { n -> execute(insertStoreSql("ST-%08d".format(n), "지점%02d".format(n))) }

            get("?page=2&size=10")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(5)
                .jsonPath("$.content[0].name")
                .isEqualTo("지점21")
                .jsonPath("$.page")
                .isEqualTo(2)
                .jsonPath("$.size")
                .isEqualTo(10)
                .jsonPath("$.totalElements")
                .isEqualTo(25)
                .jsonPath("$.totalPages")
                .isEqualTo(3)
        }

        @Test
        fun `크기는 1부터 100까지 허용한다`() {
            seed()

            get("?size=1")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
            get("?size=100").expectStatus().isOk
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(
            delimiter = '|',
            value = [
                "?status=UNKNOWN|status",
                "?status=open|status",
                "?status=OPEN,|status",
                "?ids=1,abc|ids",
                "?ids=1,,2|ids",
                "?ids=0|ids",
                "?q=가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가|q",
                "?page=-1|page",
                "?page=abc|page",
                "?size=0|size",
                "?size=101|size",
                "?size=abc|size",
            ],
        )
        fun `잘못된 조건은 400 INVALID_REQUEST이고 문제가 된 파라미터 이름을 알린다`(
            query: String,
            field: String,
        ) {
            get(query).assertInvalid(field)
        }

        @Test
        fun `ID가 101개를 넘으면 400 INVALID_REQUEST다`() {
            get("?ids=" + (1..101).joinToString(",")).assertInvalid("ids")
        }

        @Test
        fun `ID가 100개면 허용한다`() {
            get("?ids=" + (1..100).joinToString(",")).expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        private val uris = listOf("", "/1")

        @Test
        fun `매장 관리 권한과 본사 조회 권한을 가진 직원은 조회할 수 있다`() {
            seed()

            uris.forEach { uri ->
                get(uri, token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isOk
                get(uri, token(PrincipalType.EMPLOYEE, "store:viewer")).expectStatus().isOk
            }
        }

        @Test
        fun `매장 매니저 역할만, 역할 없음, 다른 서비스의 역할은 403이다`() {
            seed()

            uris.forEach { uri ->
                get(uri, token(PrincipalType.EMPLOYEE, "store:manager")).expectStatus().isForbidden
                get(uri, token(PrincipalType.EMPLOYEE)).expectStatus().isForbidden
                get(uri, token(PrincipalType.EMPLOYEE, "wms:admin")).expectStatus().isForbidden
            }
        }

        @Test
        fun `파트너와 서비스 계정은 403이다`() {
            seed()

            uris.forEach { uri ->
                get(uri, token(PrincipalType.PARTNER)).expectStatus().isForbidden
                get(uri, token(PrincipalType.SYSTEM, "store:admin")).expectStatus().isForbidden
            }
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            uris.forEach { uri ->
                get(uri, null)
                    .expectStatus()
                    .isUnauthorized
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo("UNAUTHENTICATED")
            }
        }

        @Test
        fun `인가 거부는 파라미터 검증보다 먼저다`() {
            get("/abc", token(PrincipalType.EMPLOYEE, "store:manager")).expectStatus().isForbidden
            get("?size=999", token(PrincipalType.PARTNER)).expectStatus().isForbidden
        }
    }
}
