package com.commerce.catalog.config;

import com.commerce.catalog.catalogsecurity.exceptions.CatalogAuthorizationException;
import com.commerce.catalog.catalogsecurity.exceptions.IdempotencyConflictException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalProblemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalProblemHandler.class);

    @ExceptionHandler(CatalogAuthorizationException.class)
    ProblemDetail forbidden(CatalogAuthorizationException exception) {
        LOGGER.info("Catalog authorization denied with reason=grant_missing_or_revoked");
        return problem(HttpStatus.FORBIDDEN, "urn:commerce:problem:forbidden", "Forbidden", "FORBIDDEN");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:commerce:problem:idempotency-conflict",
                "Idempotency conflict",
                "IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid.");
        problem.setType(URI.create("urn:commerce:problem:invalid-request"));
        problem.setTitle("Invalid request");
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
