package com.digimon.shop.demo.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class WebController {

    @RequestMapping("/")
    fun index(): String? {
        return "index"
    }
}