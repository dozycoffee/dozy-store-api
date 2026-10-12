package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.stream.Stream
import kotlin.test.assertEquals

@DisplayName("StoreAdminController")
class StoreAdminControllerTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    @Autowired
    private lateinit var getStore: GetStoreUseCase

    private val json = JsonMapper()

    private fun admin() = tokens.issue(type = PrincipalType.EMPLOYEE, roles = listOf("store:admin"))

    private fun post(
        body: Map<String, Any?>,
        token: String? = admin(),
    ): WebTestClient.ResponseSpec {
        val request = webTestClient.post().uri("/api/v1/admin/stores").contentType(MediaType.APPLICATION_JSON)
        return (if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request)
            .bodyValue(json.writeValueAsString(body))
            .exchange()
    }

    private fun storeCount(): Long =
        runBlocking {
            databaseClient
                .sql("SELECT count(*) AS n FROM stores")
                .map { row -> row.get("n", Long::class.javaObjectType)!! }
                .one()
                .awaitSingle()
        }

    @Nested
    @DisplayName("POST /api/v1/admin/stores — 매장 등록")
    inner class Register {
        @Test
        fun `등록하면 201과 Location, ETag, 매장 본문을 돌려준다`() {
            val result =
                post(validBody())
                    .expectStatus()
                    .isCreated
                    .expectBody()
                    .jsonPath("$.storeId")
                    .isNumber
                    .jsonPath("$.code")
                    .value<String> { assert(Regex("ST-[0-9]{8}").matches(it)) { it } }
                    .jsonPath("$.name")
                    .isEqualTo("강남역점")
                    .jsonPath("$.address.roadAddress")
                    .isEqualTo("서울특별시 강남구 강남대로 396")
                    .jsonPath("$.address.detailAddress")
                    .isEqualTo("1층")
                    .jsonPath("$.address.postalCode")
                    .isEqualTo("06232")
                    .jsonPath("$.address.sido")
                    .isEqualTo("서울특별시")
                    .jsonPath("$.address.sigungu")
                    .isEqualTo("강남구")
                    .jsonPath("$.phone")
                    .isEqualTo("02-1234-5678")
                    .jsonPath("$.status")
                    .isEqualTo("PREPARING")
                    .jsonPath("$.openingDate")
                    .isEqualTo("2026-11-01")
                    .jsonPath("$.closingDate")
                    .isEqualTo(null)
                    .jsonPath("$.version")
                    .isEqualTo(0)
                    .returnResult()

            val id = json.readTree(result.responseBody!!).get("storeId").asLong()
            assertEquals("/api/v1/admin/stores/$id", result.responseHeaders.location?.path)
            assertEquals("\"0\"", result.responseHeaders.eTag)
        }

        @Test
        fun `값이 없는 선택 필드도 응답에서 빠지지 않고 null이다`() {
            val body = validBody().also { it.address()["detailAddress"] = null }

            post(body)
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.address.detailAddress")
                .isEqualTo(null)
                .jsonPath("$.closingDate")
                .isEqualTo(null)
        }

        @Test
        fun `등록한 매장이 저장된다`() {
            val id =
                json
                    .readTree(
                        post(validBody())
                            .expectStatus()
                            .isCreated
                            .expectBody()
                            .returnResult()
                            .responseBody!!,
                    ).get("storeId")
                    .asLong()

            val stored = runBlocking { getStore.get(StoreId(id)) }

            assertEquals("강남역점", stored.name)
            assertEquals("02-1234-5678", stored.phone)
        }

        @Test
        fun `문자열의 앞뒤 공백은 제거해 저장한다`() {
            val body =
                validBody().also {
                    it["name"] = "  강남역점\t"
                    it["phone"] = " 02-1234-5678 "
                    it.address()["roadAddress"] = " 서울특별시 강남구 강남대로 396 "
                }

            post(body)
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("강남역점")
                .jsonPath("$.phone")
                .isEqualTo("02-1234-5678")
                .jsonPath("$.address.roadAddress")
                .isEqualTo("서울특별시 강남구 강남대로 396")
        }

        @Test
        fun `상세 주소가 비어 있거나 공백뿐이면 없는 것으로 저장한다`() {
            listOf("", "   ").forEachIndexed { index, blank ->
                val body =
                    validBody().also {
                        it["name"] = "상세없는점$index"
                        it.address()["detailAddress"] = blank
                    }

                post(body)
                    .expectStatus()
                    .isCreated
                    .expectBody()
                    .jsonPath("$.address.detailAddress")
                    .isEqualTo(null)
            }
        }

        @Test
        fun `상한 길이의 값은 허용한다`() {
            val body =
                validBody().also {
                    it["name"] = "가".repeat(50)
                    it["phone"] = "1".repeat(20)
                    it.address().putAll(
                        mapOf(
                            "roadAddress" to "가".repeat(100),
                            "detailAddress" to "나".repeat(100),
                            "postalCode" to "1".repeat(10),
                            "sido" to "다".repeat(30),
                            "sigungu" to "라".repeat(30),
                        ),
                    )
                }

            post(body).expectStatus().isCreated
        }

        @Test
        fun `폐점하지 않은 매장과 이름이 같으면 409 STORE_NAME_DUPLICATED다`() {
            post(validBody()).expectStatus().isCreated

            post(validBody())
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("STORE_NAME_DUPLICATED")
                .jsonPath("$.traceId")
                .isNotEmpty

            assertEquals(1, storeCount())
        }

        @Test
        fun `앞뒤 공백만 다른 이름도 같은 이름으로 본다`() {
            post(validBody()).expectStatus().isCreated

            post(validBody().also { it["name"] = " 강남역점 " })
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("STORE_NAME_DUPLICATED")
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.dozycoffee.store.management.adapter.inbound.web.StoreAdminControllerTest#invalidBodies")
        fun `요청 값이 잘못되면 400 INVALID_REQUEST이고 문제가 된 필드 이름을 알리며 저장하지 않는다`(
            label: String,
            field: String,
            mutate: (MutableMap<String, Any?>) -> Unit,
        ) {
            val body = validBody().also(mutate)

            post(body)
                .expectStatus()
                .isBadRequest
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: $field")

            assertEquals(0, storeCount())
        }

        @Test
        fun `본문이 없거나 깨졌으면 400이다`() {
            webTestClient
                .post()
                .uri("/api/v1/admin/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin()}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ 깨진 json")
                .exchange()
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        private fun token(
            type: PrincipalType = PrincipalType.EMPLOYEE,
            vararg roles: String,
        ) = tokens.issue(type = type, roles = roles.toList())

        @Test
        fun `매장 관리 권한을 가진 본사 직원은 등록할 수 있다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isCreated
        }

        @Test
        fun `다른 역할을 함께 가져도 매장 관리 권한이 있으면 등록할 수 있다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE, "store:admin", "store:viewer", "store:manager", "wms:operator"))
                .expectStatus()
                .isCreated
        }

        @Test
        fun `본사 조회 권한만으로는 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE, "store:viewer"))
                .expectStatus()
                .isForbidden
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN")

            assertEquals(0, storeCount())
        }

        @Test
        fun `매장 매니저 역할만으로는 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE, "store:manager")).expectStatus().isForbidden
        }

        @Test
        fun `역할이 없는 직원은 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE)).expectStatus().isForbidden
        }

        @Test
        fun `다른 서비스의 관리 역할로는 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.EMPLOYEE, "wms:admin", "catalog:admin")).expectStatus().isForbidden
        }

        @Test
        fun `파트너 계정은 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.PARTNER)).expectStatus().isForbidden
            assertEquals(0, storeCount())
        }

        @Test
        fun `서비스 계정은 매장 관리 역할이 있어도 등록할 수 없다`() {
            post(validBody(), token(PrincipalType.SYSTEM, "store:admin")).expectStatus().isForbidden
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            post(validBody(), token = null)
                .expectStatus()
                .isUnauthorized
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")

            assertEquals(0, storeCount())
        }

        @Test
        fun `만료된 토큰은 401이다`() {
            val expired =
                tokens.issue(
                    type = PrincipalType.EMPLOYEE,
                    roles = listOf("store:admin"),
                    issuedAt = Instant.now().minus(2, ChronoUnit.HOURS),
                    expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
                )

            post(validBody(), expired).expectStatus().isUnauthorized
        }

        @Test
        fun `다른 서비스를 대상으로 한 토큰은 401이다`() {
            val other = tokens.issue(type = PrincipalType.EMPLOYEE, roles = listOf("store:admin"), audience = listOf("wms"))

            post(validBody(), other).expectStatus().isUnauthorized
        }

        @Test
        fun `믿지 않는 키로 서명한 토큰은 401이다`() {
            val forged =
                tokens.issue(
                    type = PrincipalType.EMPLOYEE,
                    roles = listOf("store:admin"),
                    signedBy = DozyTestTokens.Key.UNTRUSTED,
                )

            post(validBody(), forged).expectStatus().isUnauthorized
        }

        @Test
        fun `인가 거부는 본문 검증보다 먼저다`() {
            // 권한이 없는 요청자에게는 어떤 필드가 잘못됐는지도 알리지 않는다.
            post(mutableMapOf("name" to null), token(PrincipalType.EMPLOYEE, "store:viewer")).expectStatus().isForbidden
        }
    }

    companion object {
        private fun validBody(): MutableMap<String, Any?> =
            mutableMapOf(
                "name" to "강남역점",
                "address" to
                    mutableMapOf<String, Any?>(
                        "roadAddress" to "서울특별시 강남구 강남대로 396",
                        "detailAddress" to "1층",
                        "postalCode" to "06232",
                        "sido" to "서울특별시",
                        "sigungu" to "강남구",
                    ),
                "phone" to "02-1234-5678",
                "openingDate" to "2026-11-01",
            )

        @Suppress("UNCHECKED_CAST")
        private fun MutableMap<String, Any?>.address(): MutableMap<String, Any?> = this["address"] as MutableMap<String, Any?>

        private fun case(
            label: String,
            field: String,
            mutate: (MutableMap<String, Any?>) -> Unit,
        ): Arguments = Arguments.of(label, field, mutate)

        @JvmStatic
        fun invalidBodies(): Stream<Arguments> =
            Stream.of(
                case("매장명이 없음", "name") { it.remove("name") },
                case("매장명이 공백뿐", "name") { it["name"] = "   " },
                case("매장명이 50자 초과", "name") { it["name"] = "가".repeat(51) },
                case("주소가 없음", "address") { it.remove("address") },
                case("도로명 주소가 없음", "address.roadAddress") { it.address().remove("roadAddress") },
                case("도로명 주소가 100자 초과", "address.roadAddress") { it.address()["roadAddress"] = "가".repeat(101) },
                case("상세 주소가 100자 초과", "address.detailAddress") { it.address()["detailAddress"] = "가".repeat(101) },
                case("우편번호가 없음", "address.postalCode") { it.address().remove("postalCode") },
                case("우편번호가 10자 초과", "address.postalCode") { it.address()["postalCode"] = "1".repeat(11) },
                case("시·도가 없음", "address.sido") { it.address().remove("sido") },
                case("시·도가 30자 초과", "address.sido") { it.address()["sido"] = "가".repeat(31) },
                case("시·군·구가 없음", "address.sigungu") { it.address().remove("sigungu") },
                case("시·군·구가 30자 초과", "address.sigungu") { it.address()["sigungu"] = "가".repeat(31) },
                case("전화번호가 없음", "phone") { it.remove("phone") },
                case("전화번호가 공백뿐", "phone") { it["phone"] = " " },
                case("전화번호가 20자 초과", "phone") { it["phone"] = "1".repeat(21) },
                case("개점 예정일이 없음", "openingDate") { it.remove("openingDate") },
                case("개점 예정일 형식이 틀림", "openingDate") { it["openingDate"] = "2026-13-45" },
                case("개점 예정일이 날짜가 아님", "openingDate") { it["openingDate"] = "내일" },
                case("주소 항목의 타입이 틀림", "address.roadAddress") { it.address()["roadAddress"] = mapOf("잘못" to "객체") },
            )
    }
}
