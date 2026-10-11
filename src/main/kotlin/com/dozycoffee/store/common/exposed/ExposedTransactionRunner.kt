package com.dozycoffee.store.common.exposed

import com.dozycoffee.store.common.TransactionRunner
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

class ExposedTransactionRunner(
    private val database: R2dbcDatabase,
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = suspendTransaction(database) { block() }
}
