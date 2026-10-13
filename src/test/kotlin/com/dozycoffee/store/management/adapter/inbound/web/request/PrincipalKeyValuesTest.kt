package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.web.InvalidRequestException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("PrincipalKeyValues")
class PrincipalKeyValuesTest {
    private val id = "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f"

    @Nested
    @DisplayName("sub 형식 (partner:uuid)")
    inner class FromSub {
        @ParameterizedTest
        @ValueSource(strings = ["partner", "employee", "system", "customer"])
        fun `Auth의 sub 형식을 읽는다`(type: String) {
            val key = PrincipalKeyValues.fromSub("principalKey", "$type:$id")

            assertEquals(type, key.type.claimValue)
            assertEquals(UUID.fromString(id), key.id)
        }

        @Test
        fun `PrincipalKey의 sub를 그대로 읽는다`() {
            val key = PrincipalKey(PrincipalType.EMPLOYEE, UUID.fromString(id))

            assertEquals(key, PrincipalKeyValues.fromSub("principalKey", key.sub))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "PARTNER:0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "partner:0199A3C4-7B2E-7C1A-9F3D-2B6E8A1C4D5F",
                " partner:0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "partner:0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f ",
                "partner",
                "partner:",
                ":0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "partner:abc",
                "partner-0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "partner::0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "unknown:0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "partner:0199a3c47b2e7c1a9f3d2b6e8a1c4d5f",
                "",
            ],
        )
        fun `형식이 틀리면 필드 이름과 함께 거부한다 (대소문자와 공백을 고쳐 주지 않는다)`(value: String) {
            val e = assertFailsWith<InvalidRequestException> { PrincipalKeyValues.fromSub("principalKey", value) }

            assertEquals("principalKey", e.field)
        }

        @Test
        fun `값이 없으면 거부한다`() {
            assertFailsWith<InvalidRequestException> { PrincipalKeyValues.fromSub("principalKey", null) }
        }
    }

    @Nested
    @DisplayName("본문의 계정 종류와 계정 ID")
    inner class Of {
        private fun of(
            type: String?,
            id: String?,
        ) = PrincipalKeyValues.of("principalType", type, "principalId", id)

        @Test
        fun `계정 종류와 계정 ID로 principal key를 만든다`() {
            assertEquals(PrincipalKey(PrincipalType.PARTNER, UUID.fromString(id)), of("partner", id))
            assertEquals(PrincipalKey(PrincipalType.EMPLOYEE, UUID.fromString(id)), of("employee", id))
        }

        @ParameterizedTest
        @ValueSource(strings = ["system", "customer"])
        fun `매니저로 지정할 수 없는 종류도 올바른 종류라 여기서는 통과한다 (도메인이 거부한다)`(type: String) {
            assertEquals(type, of(type, id).type.claimValue)
        }

        @ParameterizedTest
        @ValueSource(strings = ["admin", "PARTNER", "Partner", " partner", "partner ", ""])
        fun `모르는 계정 종류는 principalType 필드로 거부한다`(type: String) {
            val e = assertFailsWith<InvalidRequestException> { of(type, id) }

            assertEquals("principalType", e.field)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "abc",
                "",
                " 0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                "0199A3C4-7B2E-7C1A-9F3D-2B6E8A1C4D5F",
                "0199a3c47b2e7c1a9f3d2b6e8a1c4d5f",
            ],
        )
        fun `UUID 형식이 틀리면 principalId 필드로 거부한다`(value: String) {
            val e = assertFailsWith<InvalidRequestException> { of("partner", value) }

            assertEquals("principalId", e.field)
        }

        @Test
        fun `값이 없으면 각 필드 이름으로 거부한다`() {
            assertEquals("principalType", assertFailsWith<InvalidRequestException> { of(null, id) }.field)
            assertEquals("principalId", assertFailsWith<InvalidRequestException> { of("partner", null) }.field)
        }

        @Test
        fun `둘 다 틀리면 계정 종류를 먼저 알린다`() {
            assertEquals("principalType", assertFailsWith<InvalidRequestException> { of("admin", "abc") }.field)
        }
    }
}
