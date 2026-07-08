package ee.claimai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@SpringBootApplication
class ClaimaiApplication

fun main(args: Array<String>) {
    runApplication<ClaimaiApplication>(*args)
}

@Controller
class RootController {
    @GetMapping("/")
    fun index(): String = "redirect:/login"
}
