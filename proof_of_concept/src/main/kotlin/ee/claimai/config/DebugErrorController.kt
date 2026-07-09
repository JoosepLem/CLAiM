package ee.claimai.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class DebugErrorController {

    @RequestMapping("/error")
    @ResponseBody
    fun handleError(request: HttpServletRequest): String {
        val status = request.getAttribute("jakarta.servlet.error.status_code") ?: "N/A"
        val exception = request.getAttribute("jakarta.servlet.error.exception") ?: "N/A"
        val message = request.getAttribute("jakarta.servlet.error.message") ?: "N/A"
        val path = request.getAttribute("jakarta.servlet.error.request_uri") ?: "N/A"

        val stackTrace = (exception as? Throwable)?.let {
            it.stackTrace.joinToString("<br>") { "&nbsp;&nbsp;at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        } ?: "N/A"

        return """
            <h2>Status: $status</h2>
            <p><b>Path:</b> $path</p>
            <p><b>Exception:</b> ${(exception as? Throwable)?.javaClass?.name ?: exception}</p>
            <p><b>Message:</b> $message</p>
            <h3>Stack trace:</h3>
            <pre style="font-size:12px;">$stackTrace</pre>
        """.trimIndent()
    }
}
