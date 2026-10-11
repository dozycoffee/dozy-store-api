package com.dozycoffee.store.common.exposed

import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.flow.count
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("TransactionRunner")
class TransactionRunnerTest : ApplicationTest() {
    private object TxProbe : Table("tx_probe") {
        val id = integer("id")
        override val primaryKey = PrimaryKey(id)
    }

    @BeforeEach
    fun createProbe() = execute("CREATE TABLE IF NOT EXISTS tx_probe (id INTEGER PRIMARY KEY)")

    @AfterEach
    fun dropProbe() = execute("DROP TABLE IF EXISTS tx_probe")

    private fun count(): Int = tx { TxProbe.selectAll().count().toInt() }

    @Test
    fun `블록이 끝나면 커밋한다`() {
        tx { TxProbe.insert { it[id] = 1 } }

        assertEquals(1, count())
    }

    @Test
    fun `블록이 예외로 끝나면 변경을 모두 롤백한다`() {
        assertFailsWith<IllegalStateException> {
            tx {
                TxProbe.insert { it[id] = 1 }
                error("실패")
            }
        }

        assertEquals(0, count())
    }

    @Test
    fun `안쪽에서 다시 열면 바깥 트랜잭션을 이어 써서 함께 롤백된다`() {
        assertFailsWith<IllegalStateException> {
            tx {
                TxProbe.insert { it[id] = 1 }
                transactionRunner.inTransaction { TxProbe.insert { it[id] = 2 } }
                error("바깥에서 실패")
            }
        }

        assertEquals(0, count())
    }

    @Test
    fun `안쪽 트랜잭션은 바깥 변경을 볼 수 있다`() {
        val seen =
            tx {
                TxProbe.insert { it[id] = 1 }
                transactionRunner.inTransaction { TxProbe.selectAll().count().toInt() }
            }

        assertEquals(1, seen)
    }

    @Test
    fun `DB 오류가 나도 블록을 다시 실행하지 않는다`() {
        var runs = 0
        assertFailsWith<Exception> {
            tx {
                runs++
                TxProbe.insert { it[id] = 1 }
                TxProbe.insert { it[id] = 1 }
            }
        }

        assertEquals(1, runs)
    }
}
