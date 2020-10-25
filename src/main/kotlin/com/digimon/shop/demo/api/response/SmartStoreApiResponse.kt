package com.digimon.shop.demo.api.response

data class SmartStoreApiResponse(
    val items: List<Map<String, String>>?,
    val total: Int? = 0,
) {

}