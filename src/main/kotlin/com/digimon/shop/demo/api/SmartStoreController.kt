package com.digimon.shop.demo.api

import com.digimon.shop.demo.api.response.SmartStoreApiResponse
import com.digimon.shop.demo.api.response.SmartStoreSearchResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import kotlin.math.abs

@RestController
@RequestMapping("/api/v1/smart-store")
class SmartStoreController(
    val webClientBuilder: WebClient.Builder,
    @Value("\${naver.search.client-id}") val clientId: String,
    @Value("\${naver.search.client-secret}") val clientSecret: String,
    @Value("\${naver.search.break-count}") val breakCount: Int,
    @Value("\${naver.search.request-delay-ms}") val delay: Long,
) {

    companion object {
        val logger = LoggerFactory.getLogger(SmartStoreController::class.java)
        val TIME_OUT_MILLIS = 5000
        const val REVIEW_ERROR = "리뷰 카운트를 가져오는데 실패했습니다."
        const val TAG_ERROR = "태그를 가져오는데 실패했습니다."
        const val LINK_ERROR = "링크를 가져오는데 실패했습니다."
        const val DESCRIPTION_ERROR = "설명을 가져오는데 실패했습니다."
    }

    val webClient by lazy { webClientBuilder.build() }

    @GetMapping("/shops/items/{keyword}")
    fun getItem(
        @RequestParam(required = false, defaultValue = "") url: String,
        @PathVariable keyword: String,
    ): ResponseEntity<SmartStoreSearchResponse> {
        var offset = 1
        if (url.isBlank()) {
            val result = searchFromSmartStoreAPI(keyword, offset)
            return responseEntity(result.items?.get(0), result.total, page = 1, ranking = 1)
        }
        var value: Map<String, String>? = null
        var finished = false
        var apiResponse: SmartStoreApiResponse
        var indexOf = 0
        var page = 0
        var ranking = 1
        while (true) {
            Thread.sleep(delay)
            apiResponse = try {
                searchFromSmartStoreAPI(keyword, offset)
            } catch (e: Exception) {
                logger.error(e.message)
                offset = breakCount
                finished = true
                searchFromSmartStoreAPI(keyword, offset)
            }
            val items = apiResponse.items
            val filtered = items?.filter { item ->
                val link = (item["link"] as String)
                link.trim().toLowerCase() == url.trim().toLowerCase()
            }
            if (!filtered.isNullOrEmpty()) {
                value = filtered[0]
                ranking = items.indexOf(value)
                indexOf += (offset + ranking)
                break
            }
            offset += 9
            if (abs(offset % 2) == 0) {
                page++
            }

            if (finished) break
        }
        return responseEntity(value, apiResponse.total, page, ranking - 1)
    }

    private fun searchFromSmartStoreAPI(keyword: String, offset: Int): SmartStoreApiResponse {
        val url = "https://openapi.naver.com/v1/search/shop.json?query=${keyword}&start=${offset}"
        val map = webClient.get()
            .uri(url)
            .header("X-Naver-Client-Id", clientId)
            .header("X-Naver-Client-Secret", clientSecret)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()
        val items = map?.get("items") as List<Map<String, String>>
        val total = map?.get("total") as Int
        return SmartStoreApiResponse(
            items = items,
            total = total,
        )
    }

    private fun getRealLink(link: String?): String {
        return try {
            val doc = Jsoup.connect(link!!).timeout(TIME_OUT_MILLIS).get()
            val script = doc.select("script").html()
            val realLink: String? = script.substring(script.indexOf("http"), script.lastIndexOf("'"))
            realLink!!
        } catch (e: Exception) {
            logger.error(e.message)
            LINK_ERROR
        }
    }

    private fun getMetadata(link: String): Map<String, String> {
        val doc = try {
            Jsoup.connect(link).timeout(TIME_OUT_MILLIS).get()
        } catch (e: Exception) {
            return mapOf(
                "title" to "",
                "image" to "",
                "description" to DESCRIPTION_ERROR,
                "reviewCount" to REVIEW_ERROR,
                "tags" to TAG_ERROR
            )
        }
        val metaTags = doc.getElementsByTag("meta")
        var title = ""
        var image = ""
        var description = ""
        metaTags.forEach { metaTag ->
            val property = metaTag.attr("property")
            if ("og:title" == property) {
                title = metaTag.attr("content")
            }
            if ("og:description" == property) {
                description = metaTag.attr("content")
            }
            if ("og:image" == property) {
                image = metaTag.attr("content")
            }
        }
        val reviewCount = try {
            val anchors = doc.getElementsByTag("a")
            var reviewTag: Element? = null
            anchors.forEach { a ->
                if ("#REVIEW" == a.attr("href").trim()) {
                    reviewTag = a
                }
            }
            if (reviewTag == null) throw NullPointerException()
            reviewTag!!.getElementsByTag("strong").text()
        } catch (e: Exception) {
            logger.error(e.message)
            REVIEW_ERROR
        }
        val tags = try {
            doc.select("[name=keywords]").attr("content")
        } catch (e: Exception) {
            logger.error(e.message)
            TAG_ERROR
        }

        return mapOf("title" to title, "image" to image, "description" to description, "reviewCount" to reviewCount, "tags" to tags)
    }

    private fun responseEntity(item: Map<String, String>?, total: Int?, page: Int = 1, ranking: Int = 1) =
        if (item.isNullOrEmpty() || total == null) {
            ResponseEntity.notFound().build()
        } else {
            val highestPrice = item["hprice"]
            val lowestPrice = item["lprice"]
            val link = getRealLink(item["link"])
            val category = "${item["category1"]}>${item["category2"]}>${item["category3"]}>${item["category4"]}"
            val response = if ((highestPrice == null || highestPrice.toInt() == 0)) {
                val metadata = getMetadata(link)
                SmartStoreSearchResponse(
                    total = total,
                    title = if (metadata["title"].isNullOrBlank()) item["title"] else metadata["title"],
                    link = link,
                    image = if (metadata["image"].isNullOrBlank()) item["image"] else metadata["image"],
                    price = lowestPrice,
                    category = category,
                    description = metadata["description"],
                    reviewCount = metadata["reviewCount"],
                    tags = metadata["tags"],
                    page = page,
                    ranking = ranking,
                )
            } else {
                SmartStoreSearchResponse(
                    total = total,
                    title = item["title"],
                    link = link,
                    image = item["image"],
                    price = lowestPrice,
                    highestPrice = highestPrice,
                    category = category,
                    page = page,
                    ranking = ranking,
                )
            }
            ResponseEntity.ok(response)
        }
}