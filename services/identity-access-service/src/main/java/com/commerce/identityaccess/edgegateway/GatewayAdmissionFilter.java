package com.commerce.identityaccess.edgegateway;

import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

final class GatewayAdmissionFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {
    private final Semaphore admission;

    GatewayAdmissionFilter(int capacity) {
        admission = new Semaphore(capacity);
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!admission.tryAcquire()) {
            throw new GatewayRequestRejectedException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
        try {
            return next.handle(request);
        } finally {
            admission.release();
        }
    }
}
