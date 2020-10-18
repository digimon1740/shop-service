package com.digimon.shop.demo.api

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1")
class RootController(
    val webClientBuilder: WebClient.Builder,
    @Value("\${naver.search.client-id}") val clientId: String,
    @Value("\${naver.search.client-secret}") val clientSecret: String,
    @Value("\${naver.search.break-count}") val breakCount: Int,
    @Value("\${naver.search.request-delay-ms}") val delay: Long,
) {

    companion object {
        val log = LoggerFactory.getLogger(this.javaClass)
    }

    val webClient by lazy { webClientBuilder.build() }

    @GetMapping("/shops/items/{keyword}")
    fun getItem(
        @RequestParam query: String,
        @PathVariable keyword: String,
    ): Mono<LinkedHashMap<String, String>> {
        var value: LinkedHashMap<String, String>? = null
        var offset = 1
        var finished = false
        while (true) {
            val url = "https://openapi.naver.com/v1/search/shop.json?query=${keyword}&start=${offset}"
            Thread.sleep(delay)
            val map = try {
                webClient.get()
                    .uri(url)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()
            } catch (e: Exception) {
                log.error(e.message)
                offset = breakCount
                finished = true
                val url = "https://openapi.naver.com/v1/search/shop.json?query=${keyword}&start=${offset}"
                webClient.get()
                    .uri(url)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()
            }

            val items = map?.get("items") as List<LinkedHashMap<String, String>>
            val filtered = items.filter { item ->
                val link = (item["link"] as String)
                link.trim().toLowerCase() == query.trim().toLowerCase()
            }
            if (filtered.isNotEmpty()) {
                value = filtered[0]
                break
            }
            offset += 10
            if (finished) break
        }
        return Mono.justOrEmpty(value)
    }
}