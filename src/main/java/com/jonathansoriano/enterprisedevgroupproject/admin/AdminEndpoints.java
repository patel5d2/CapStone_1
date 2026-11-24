package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.exception.ExceptionWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * What the two admin controllers share: how a request body that cannot be read is answered.
 *
 * <p>A body that is malformed, or carries a value outside an enum (a support request status
 * that does not exist), is the caller's mistake and is answered 400. It lives here rather
 * than in the global {@code ExceptionTranslator} on purpose: there it would also change what
 * the existing student endpoints answer for the same mistake (a 500 today, pinned by
 * {@code StudentControllerTest}), and this feature is not the place to change that. A handler
 * on the controller is consulted before any {@code @ControllerAdvice}, so it applies to
 * {@code /api/admin} and nothing else.
 */
@Slf4j
abstract class AdminEndpoints {

    static final String UNREADABLE_MESSAGE = "The request could not be read. Check the values and try again.";

    /** The parser's own message is logged, not returned: it quotes class names. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ExceptionWrapper> unreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Unreadable request body at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                new ExceptionWrapper(HttpStatus.BAD_REQUEST.value(), UNREADABLE_MESSAGE, request.getRequestURI()));
    }
}
