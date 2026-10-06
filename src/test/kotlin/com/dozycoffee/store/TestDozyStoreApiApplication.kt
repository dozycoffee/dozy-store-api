package com.dozycoffee.store

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<DozyStoreApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
