package com.dozycoffee.store.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

/** API 테스트의 뿌리. 인증된 요청자는 `auth-test`의 `@WithDozyPrincipal`로 만든다. */
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTest : IntegrationTest() {
    @Autowired
    protected lateinit var webTestClient: WebTestClient
}
