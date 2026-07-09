package ee.claimai.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class DebugErrorController : ErrorController {

    @RequestMapping("/error")
    @ResponseBody
    fun handleError(request: HttpServletRequest): String {
        val status = request.getAttribute("jakarta.servlet.error.status_code") ?: "N/A"
        val exception = request.getAttribute("jakarta.servlet.error.exception") ?: "N/A"
        val message = request.getAttribute("jakarta.servlet.error.message") ?: "N/A"
        val path = request.getAttribute("jakarta.servlet.error.request_uri") ?: "N/A"

        val stackTrace = (exception as? Throwable)?.let {
            it.stackTrace.joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        } ?: "N/A"

        return """
            Status: $status
            Path: $path
            Exception: ${(exception as? Throwable)?.javaClass?.name ?: exception}
            Message: $message
            
            Stack trace:
            $stackTrace
        """.trimIndent()
    }
}
