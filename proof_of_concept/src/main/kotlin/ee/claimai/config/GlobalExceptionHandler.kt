package ee.claimai.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import java.net.URI

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: WebRequest): ResponseEntity<ProblemDetail> {
        log.warn("Resource not found: {}", ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problem.instance = URI.create(request.getDescription(false).removePrefix("uri="))
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<ProblemDetail> {
        log.warn("Bad request: {}", ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")
        problem.instance = URI.create(request.getDescription(false).removePrefix("uri="))
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleInternalError(ex: IllegalStateException, request: WebRequest): ResponseEntity<ProblemDetail> {
        log.error("Internal state error: {}", ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
        problem.instance = URI.create(request.getDescription(false).removePrefix("uri="))
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: WebRequest): ResponseEntity<ProblemDetail> {
        log.warn("Access denied: {}", ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied")
        problem.instance = URI.create(request.getDescription(false).removePrefix("uri="))
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception, request: WebRequest): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception for {}: {}", request.getDescription(false), ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
        problem.instance = URI.create(request.getDescription(false).removePrefix("uri="))
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}
