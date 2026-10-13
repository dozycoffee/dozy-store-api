package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.auth.core.AuthenticatedPrincipal
import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.core.Realm
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("Requester")
class RequesterTest {
    private val key = PrincipalKey(PrincipalType.EMPLOYEE, UUID.fromString("0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f"))

    @Test
    fun `토큰의 principal key와 역할 코드를 그대로 담는다`() {
        val principal = AuthenticatedPrincipal(key, Realm.INTERNAL, setOf("admin", "manager"), "session-1")

        val requester = Requester.from(principal)

        assertEquals(key, requester.principal)
        assertEquals(setOf("admin", "manager"), requester.roles)
    }

    @Test
    fun `역할이 없는 토큰은 빈 역할로 담는다`() {
        val principal = AuthenticatedPrincipal(PrincipalKey(PrincipalType.PARTNER, key.id), Realm.PARTNER, emptySet(), null)

        val requester = Requester.from(principal)

        assertEquals(PrincipalType.PARTNER, requester.principal.type)
        assertEquals(emptySet(), requester.roles)
    }
}
