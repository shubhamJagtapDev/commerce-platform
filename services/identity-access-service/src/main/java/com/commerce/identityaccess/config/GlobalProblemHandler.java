package com.commerce.identityaccess.config;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalProblemHandler {

    @ExceptionHandler(AuthenticationFailureException.class)
    ProblemDetail authenticationFailed(AuthenticationFailureException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "urn:commerce:problem:authentication-failed", "Authentication failed");
    }

    @ExceptionHandler(MissingSessionException.class)
    ProblemDetail missingSession(MissingSessionException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "urn:commerce:problem:missing-session", "Authentication required");
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
}
