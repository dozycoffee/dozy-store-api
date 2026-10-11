package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.management.application.port.outbound.StoreCodeGenerator
import com.dozycoffee.store.support.ApplicationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@DisplayName("ExposedStoreCodeGenerator")
class ExposedStoreCodeGeneratorTest : ApplicationTest() {
    @Autowired
    private lateinit var generator: StoreCodeGenerator

    @Test
    fun `ST- 접두사와 8자리 일련번호 형식의 코드를 발급한다`() {
        val code = tx { generator.next() }

        assertTrue(Regex("ST-[0-9]{8}").matches(code.value), code.value)
    }

    @Test
    fun `발급할 때마다 다른 코드가 나온다`() {
        val first = tx { generator.next() }
        val second = tx { generator.next() }

        assertNotEquals(first, second)
    }

    @Test
    fun `트랜잭션이 롤백돼도 발급한 코드는 다시 나오지 않는다`() {
        val rolledBack =
            runCatching {
                tx {
                    val code = generator.next()
                    error("롤백: ${code.value}")
                }
            }.exceptionOrNull()!!.message!!.removePrefix("롤백: ")

        val next = tx { generator.next() }

        // 8자리로 0을 채운 코드라서 문자열 순서가 숫자 순서와 같다. 롤백된 번호 뒤의 번호가 나온다.
        assertTrue(next.value > rolledBack, "롤백된 $rolledBack 다음 코드는 그보다 커야 합니다: ${next.value}")
    }
}
