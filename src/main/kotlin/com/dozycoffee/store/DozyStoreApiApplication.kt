package com.dozycoffee.store

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DozyStoreApiApplication

fun main(args: Array<String>) {
    runApplication<DozyStoreApiApplication>(*args)
}
