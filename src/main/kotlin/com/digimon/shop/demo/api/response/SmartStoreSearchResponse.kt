package com.digimon.shop.demo.api.response

data class SmartStoreSearchResponse(
    val total: Int? = 0,
    val title: String? = null,
    val image: String? = null,
    val link: String? = null,
    val page: Int? = null,
    val ranking: Int? = null,
    val category: String? = null,
    val price: String? = null,
    val highestPrice : String? = null,
    val reviewCount: String? = "0",
    val description: String? = null,
    val tags: String? = null,
)