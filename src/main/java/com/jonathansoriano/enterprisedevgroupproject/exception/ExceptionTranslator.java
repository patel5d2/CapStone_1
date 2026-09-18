package com.jonathansoriano.enterprisedevgroupproject.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// This class acts as a global exception handler for the entire application.
// It intercepts exceptions thrown from controllers/services and maps them to structured HTTP responses.

@Slf4j
@ControllerAdvice()
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExceptionTranslator {
    // PURPOSE OF THIS CLASS: It catches exceptions thrown in your app’s
    // controller/service layer and turns them into HTTP responses.

    // Any time this "SearchNotFoundException" is thrown, this class will redirect
    // here
    @ExceptionHandler(SearchNotFoundException.class)
    public ResponseEntity<ExceptionWrapper> handleSearchNotFoundException(SearchNotFoundException ex,
            HttpServletRequest request) {
        //Logging Exceptions
        log.warn("Search not found at {}: ", request.getRequestURI(), ex);
        // We retrieve said exception's message and return it in the ResponseEntity
        // along with HTTP status (404).

        ExceptionWrapper wrapper = new ExceptionWrapper(HttpStatus.NOT_FOUND.value(), ex.getMessage(),
                request.getRequestURI());

        return new ResponseEntity<>(wrapper, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<ExceptionWrapper> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Email already exists at {}: ", request.getRequestURI(), ex);

        ExceptionWrapper wrapper = new ExceptionWrapper(HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI());

        return new ResponseEntity<>(wrapper, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionWrapper> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {

        // Sorted, so the same invalid request always produces the same response: a HashMap
        // ordered these arbitrarily, which made the message untestable and the order of
        // reported problems shift between identical calls.
        Map<String, String> fieldErrors = new TreeMap<>();
        List<String> otherErrors = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            // Not every error is a FieldError — a class-level constraint produces an
            // ObjectError, and the unchecked cast this used to do turned one into a
            // ClassCastException, which the catch-all then served as an opaque 500.
            if (error instanceof FieldError fieldError) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                otherErrors.add(error.getDefaultMessage());
            }
        });

        // A sentence a person can read, rather than a Java map's toString(). The
        // machine-readable form travels beside it in fieldErrors.
        String message = Stream.concat(fieldErrors.values().stream(), otherErrors.stream())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        log.warn("Property validation error(s) at {}: ", request.getRequestURI(), ex);

        ExceptionWrapper wrapper = new ExceptionWrapper(HttpStatus.BAD_REQUEST.value(),
                message.isBlank() ? "Some fields need attention." : message,
                request.getRequestURI(), fieldErrors);

        return new ResponseEntity<>(wrapper, HttpStatus.BAD_REQUEST);
    }

    // Carries its own status (e.g. a request whose Clerk token has no email claim).
    // Without this handler the catch-all below would turn it into a 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ExceptionWrapper> handleResponseStatusException(ResponseStatusException ex,
            HttpServletRequest request) {
        log.warn("Request rejected at {}: ", request.getRequestURI(), ex);

        ExceptionWrapper wrapper = new ExceptionWrapper(ex.getStatusCode().value(), ex.getReason(),
                request.getRequestURI());

        return new ResponseEntity<>(wrapper, ex.getStatusCode());
    }

    // Any other exception thrown will be caught here
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionWrapper> handleException(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage();
        //Logging Exceptions
        log.error("Unhandled exception occurred at {}: ", request.getRequestURI(), ex);

        ExceptionWrapper wrapper = new ExceptionWrapper(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong..." + message, request.getRequestURI());
        return new ResponseEntity<>(wrapper, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
