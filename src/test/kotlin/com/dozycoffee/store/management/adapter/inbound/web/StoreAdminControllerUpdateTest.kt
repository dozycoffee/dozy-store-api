package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

@DisplayName("StoreAdminController 수정")
class StoreAdminControllerUpdateTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    private val json = JsonMapper()

    private fun token(
        type: PrincipalType = PrincipalType.EMPLOYEE,
        vararg roles: String,
    ) = tokens.issue(type = type, roles = roles.toList())

    private fun admin() = token(PrincipalType.EMPLOYEE, "store:admin")

    private fun body(
        name: String? = "서초점",
        openingDate: String? = "2026-11-01",
    ): MutableMap<String, Any?> =
        mutableMapOf(
            "name" to name,
            "address" to
                mapOf(
                    "roadAddress" to "서울특별시 서초구 서초대로 100",
                    "detailAddress" to null,
                    "postalCode" to "06500",
                    "sido" to "서울특별시",
                    "sigungu" to "서초구",
                ),
            "phone" to "02-9999-0000",
            "openingDate" to openingDate,
        )

    private fun put(
        path: String = "/1",
        ifMatch: String? = "\"0\"",
        body: Map<String, Any?> = body(),
        token: String? = admin(),
    ): WebTestClient.ResponseSpec {
        var request = webTestClient.put().uri("/api/v1/admin/stores$path").contentType(MediaType.APPLICATION_JSON)
        if (token != null) request = request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        if (ifMatch != null) request = request.header(HttpHeaders.IF_MATCH, ifMatch)
        return request.bodyValue(json.writeValueAsString(body)).exchange()
    }

    private fun getStore(path: String = "/1") =
        webTestClient
            .get()
            .uri("/api/v1/admin/stores$path")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(PrincipalType.EMPLOYEE, "store:viewer")}")
            .exchange()

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private fun seed(
        status: String = "PREPARING",
        name: String = "강남역점",
    ) {
        execute(
            insertStoreSql(
                "ST-00000001",
                name,
                status,
                closingDate = "2027-03-31".takeIf { status == "CLOSED" },
                sido = "서울특별시",
                sigungu = "강남구",
            ),
        )
    }

    private fun WebTestClient.ResponseSpec.assertProblem(
        status: Int,
        code: String,
    ) {
        expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo(code)
            .jsonPath("$.traceId")
            .isNotEmpty
    }

    /** 저장된 값이 바뀌지 않았는지 확인한다. */
    private fun assertUnchanged(
        name: String = "강남역점",
        version: Long = 0,
    ) {
        getStore()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo(name)
            .jsonPath("$.version")
            .isEqualTo(version.toInt())
    }

    @Nested
    @DisplayName("성공")
    inner class Success {
        @Test
        fun `정보를 바꾸면 200과 바뀐 값, 버전 +1, 새 ETag를 돌려준다`() {
            seed()

            val result =
                put()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.storeId")
                    .isEqualTo(1)
                    .jsonPath("$.name")
                    .isEqualTo("서초점")
                    .jsonPath("$.address.roadAddress")
                    .isEqualTo("서울특별시 서초구 서초대로 100")
                    .jsonPath("$.address.detailAddress")
                    .isEqualTo(null)
                    .jsonPath("$.address.sigungu")
                    .isEqualTo("서초구")
                    .jsonPath("$.phone")
                    .isEqualTo("02-9999-0000")
                    .jsonPath("$.version")
                    .isEqualTo(1)
                    .returnResult()

            assertEquals("\"1\"", result.responseHeaders.eTag)
        }

        @Test
        fun `매장 코드와 상태와 폐점일은 바뀌지 않는다`() {
            seed(status = "OPEN")

            put()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ST-00000001")
                .jsonPath("$.status")
                .isEqualTo("OPEN")
                .jsonPath("$.closingDate")
                .isEqualTo(null)
        }

        @Test
        fun `바뀐 값이 저장되어 조회된다`() {
            seed()

            put().expectStatus().isOk

            getStore()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("서초점")
                .jsonPath("$.phone")
                .isEqualTo("02-9999-0000")
                .jsonPath("$.version")
                .isEqualTo(1)
        }

        @Test
        fun `응답의 ETag로 이어서 수정할 수 있다`() {
            seed()
            val etag =
                put(body = body(name = "서초점"))
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseHeaders.eTag!!

            put(ifMatch = etag, body = body(name = "역삼점"))
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("역삼점")
                .jsonPath("$.version")
                .isEqualTo(2)
        }

        @Test
        fun `앞뒤 공백은 제거해 저장한다`() {
            seed()

            put(body = body(name = "  서초점\t"))
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("서초점")
        }

        @Test
        fun `이름을 그대로 두고 다른 정보만 고치는 것은 이름 중복이 아니다`() {
            seed()

            put(body = body(name = "강남역점"))
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("강남역점")
        }

        @Test
        fun `폐점한 매장의 이름으로는 바꿀 수 있다`() {
            seed()
            execute(insertStoreSql("ST-00000002", "옛점", "CLOSED", closingDate = "2027-03-31"))

            put(body = body(name = "옛점")).expectStatus().isOk
        }

        @Test
        fun `개점 준비 중인 매장은 개점 예정일을 바꿀 수 있다`() {
            seed(status = "PREPARING")

            put(body = body(openingDate = "2026-12-25"))
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.openingDate")
                .isEqualTo("2026-12-25")
        }

        @ParameterizedTest
        @EnumSource(value = StatusForTest::class)
        fun `개점 준비가 아니어도 개점일을 그대로 두면 다른 정보는 고칠 수 있다 (폐점한 매장 포함)`(status: StatusForTest) {
            seed(status = status.name)

            put(body = body(openingDate = "2026-11-01"))
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("서초점")
                .jsonPath("$.status")
                .isEqualTo(status.name)
        }
    }

    @Nested
    @DisplayName("규칙 위반")
    inner class Rules {
        @ParameterizedTest
        @EnumSource(value = StatusForTest::class)
        fun `개점 준비가 아니면 개점일을 바꿀 때 409 OPENING_DATE_NOT_CHANGEABLE이고 저장하지 않는다`(status: StatusForTest) {
            seed(status = status.name)

            put(body = body(openingDate = "2026-12-25")).assertProblem(409, "OPENING_DATE_NOT_CHANGEABLE")

            assertUnchanged()
        }

        @Test
        fun `폐점하지 않은 다른 매장과 이름이 같으면 409 STORE_NAME_DUPLICATED이고 저장하지 않는다`() {
            seed()
            execute(insertStoreSql("ST-00000002", "서초점", "OPEN"))

            put(body = body(name = "서초점")).assertProblem(409, "STORE_NAME_DUPLICATED")

            assertUnchanged()
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            put(path = "/999").assertProblem(404, "STORE_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("If-Match")
    inner class IfMatch {
        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED이고 저장하지 않는다`() {
            seed()

            put(ifMatch = null).assertProblem(428, "VERSION_REQUIRED")

            assertUnchanged()
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "W/\"0\"", "*", "\"abc\"", "\"-1\"", "\"0\", \"1\""])
        fun `형식이 틀리면 400 INVALID_REQUEST이고 If-Match를 알린다`(header: String) {
            seed()

            put(ifMatch = header)
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: If-Match")

            assertUnchanged()
        }

        @Test
        fun `오래된 버전이면 409 VERSION_CONFLICT이고 현재 버전을 알리며 저장하지 않는다`() {
            seed()
            execute("UPDATE stores SET version = 3 WHERE id = 1")

            put(ifMatch = "\"2\"")
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VERSION_CONFLICT")
                .jsonPath("$.currentVersion")
                .isEqualTo(3)

            assertUnchanged(version = 3)
        }

        @Test
        fun `같은 버전으로 두 번 수정하면 두 번째는 409다`() {
            seed()

            put(body = body(name = "서초점")).expectStatus().isOk
            put(body = body(name = "역삼점")).assertProblem(409, "VERSION_CONFLICT")

            assertUnchanged(name = "서초점", version = 1)
        }

        @Test
        fun `같은 버전으로 동시에 수정하면 하나만 성공한다`() {
            seed()
            val attempts = 6

            val statuses =
                runBlocking {
                    (1..attempts)
                        .map { n ->
                            async(Dispatchers.Default) { put(body = body(name = "동시$n")).returnResult(String::class.java).status.value() }
                        }.awaitAll()
                }

            assertEquals(1, statuses.count { it == 200 })
            assertEquals(attempts - 1, statuses.count { it == 409 })
            getStore()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("요청 값 검증")
    inner class Validation {
        @Test
        fun `등록과 같은 규칙으로 값을 검증하고 필드 이름을 알린다`() {
            seed()

            listOf(
                body(name = null) to "name",
                body(name = "   ") to "name",
                body(name = "가".repeat(51)) to "name",
                body(openingDate = null) to "openingDate",
                body(openingDate = "2026-13-45") to "openingDate",
                body().also { it.remove("address") } to "address",
                body().also { it["phone"] = "1".repeat(21) } to "phone",
            ).forEach { (invalid, field) ->
                put(body = invalid)
                    .expectStatus()
                    .isBadRequest
                    .expectBody()
                    .jsonPath("$.detail")
                    .isEqualTo("요청 값이 올바르지 않습니다: $field")
            }

            assertUnchanged()
        }

        @ParameterizedTest
        @ValueSource(strings = ["abc", "0", "-1"])
        fun `매장 ID가 양의 정수가 아니면 400이다`(value: String) {
            put(path = "/$value")
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: storeId")
        }

        @Test
        fun `If-Match 확인은 본문 검증보다 먼저다`() {
            seed()

            put(ifMatch = null, body = body(name = null)).assertProblem(428, "VERSION_REQUIRED")
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        @Test
        fun `매장 관리 권한을 가진 본사 직원만 수정할 수 있다`() {
            seed()

            put(token = token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isOk
        }

        @Test
        fun `본사 조회, 매장 매니저 역할만, 역할 없음, 다른 서비스 역할은 403이고 저장하지 않는다`() {
            seed()

            listOf(
                token(PrincipalType.EMPLOYEE, "store:viewer"),
                token(PrincipalType.EMPLOYEE, "store:manager"),
                token(PrincipalType.EMPLOYEE),
                token(PrincipalType.EMPLOYEE, "wms:admin"),
            ).forEach { put(token = it).assertProblem(403, "FORBIDDEN") }

            assertUnchanged()
        }

        @Test
        fun `파트너와 서비스 계정은 403이다`() {
            seed()

            put(token = token(PrincipalType.PARTNER)).expectStatus().isForbidden
            put(token = token(PrincipalType.SYSTEM, "store:admin")).expectStatus().isForbidden
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            seed()

            put(token = null).assertProblem(401, "UNAUTHENTICATED")
        }

        @Test
        fun `인가 거부는 If-Match 확인과 본문 검증보다 먼저다`() {
            seed()
            val viewer = token(PrincipalType.EMPLOYEE, "store:viewer")

            put(token = viewer, ifMatch = null).assertProblem(403, "FORBIDDEN")
            put(token = viewer, body = body(name = null)).assertProblem(403, "FORBIDDEN")
            put(token = viewer, path = "/abc").assertProblem(403, "FORBIDDEN")
        }
    }

    /** 개점 준비가 아닌 상태. 개점일 변경 규칙을 상태별로 확인한다. */
    enum class StatusForTest { OPEN, SUSPENDED, CLOSED }
}
