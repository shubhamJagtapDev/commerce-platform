package com.commerce.identityaccess.config;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.exceptions.RegistrationRateExceededException;
import com.commerce.identityaccess.auth.exceptions.RegistrationUnavailableException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnedResourceNotFoundException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnershipRequiredException;
import com.commerce.identityaccess.edgegateway.GatewayRequestRejectedException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalProblemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalProblemHandler.class);

    @ExceptionHandler(AuthenticationFailureException.class)
    ProblemDetail authenticationFailed(AuthenticationFailureException exception) {
        LOGGER.info("BFF authentication rejected with code={}", exception.code());
        ProblemDetail problem =
                problem(HttpStatus.UNAUTHORIZED, "urn:commerce:problem:authentication-failed", "Authentication failed");
        problem.setProperty("code", "AUTHENTICATION_FAILED");
        return problem;
    }

    @ExceptionHandler(MissingSessionException.class)
    ProblemDetail missingSession(MissingSessionException exception) {
        ProblemDetail problem =
                problem(HttpStatus.UNAUTHORIZED, "urn:commerce:problem:missing-session", "Authentication required");
        problem.setProperty("code", "AUTHENTICATION_REQUIRED");
        return problem;
    }

    @ExceptionHandler(CustomerOwnedResourceNotFoundException.class)
    ProblemDetail customerOwnedResourceNotFound(CustomerOwnedResourceNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "urn:commerce:problem:not-found", "Not found");
        problem.setProperty("code", "NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(CustomerOwnershipRequiredException.class)
    ProblemDetail customerOwnershipRequired(CustomerOwnershipRequiredException exception) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "urn:commerce:problem:forbidden", "Forbidden");
        problem.setProperty("code", "FORBIDDEN");
        return problem;
    }

    @ExceptionHandler(RegistrationUnavailableException.class)
    ProblemDetail registrationUnavailable(RegistrationUnavailableException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "urn:commerce:problem:not-found", "Not found");
        problem.setProperty("code", "NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(RegistrationRateExceededException.class)
    ResponseEntity<ProblemDetail> registrationRateExceeded(RegistrationRateExceededException exception) {
        ProblemDetail problem = problem(
                HttpStatus.TOO_MANY_REQUESTS, "urn:commerce:problem:registration-rate-exceeded", "Too many requests");
        problem.setProperty("code", "REGISTRATION_RATE_EXCEEDED");
        long retryAfterSeconds = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(GatewayRequestRejectedException.class)
    ProblemDetail gatewayRejected(GatewayRequestRejectedException exception) {
        String type = "urn:commerce:problem:"
                + exception.code().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        ProblemDetail problem = problem(exception.status(), type, "Gateway request rejected");
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail catalogDependencyFailed(ResourceAccessException exception) {
        boolean timedOut = causedByTimeout(exception);
        HttpStatus status = timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        String code = timedOut ? "GATEWAY_TIMEOUT" : "DEPENDENCY_UNAVAILABLE";
        ProblemDetail problem = problem(
                status,
                timedOut ? "urn:commerce:problem:gateway-timeout" : "urn:commerce:problem:dependency-unavailable",
                timedOut ? "Gateway timeout" : "Dependency unavailable");
        problem.setProperty("code", code);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid.");
        problem.setType(URI.create("urn:commerce:problem:invalid-request"));
        problem.setTitle("Invalid request");
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String type, String title) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        return problem;
    }

    private boolean causedByTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
