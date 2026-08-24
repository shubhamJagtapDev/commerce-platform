package com.commerce.catalog.catalogsecurity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/authorization-probes")
public class CatalogAuthorizationProbeController {
    private final CreateAuthorizationProbe createAuthorizationProbe;

    CatalogAuthorizationProbeController(CreateAuthorizationProbe createAuthorizationProbe) {
        this.createAuthorizationProbe = createAuthorizationProbe;
    }

    @PostMapping
    ResponseEntity<AuthorizationProbeResponse> create(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizationProbeRequest request) {
        var token = authentication.getToken();
        CreateAuthorizationProbe.Result result = createAuthorizationProbe.create(
                Objects.requireNonNull(token.getIssuer()).toString(),
                Objects.requireNonNull(token.getSubject()),
                idempotencyKey,
                request.purpose());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(new AuthorizationProbeResponse(result.probeId(), result.version(), result.committedAt()));
    }

    record AuthorizationProbeRequest(
            @NotBlank @Pattern(regexp = CreateAuthorizationProbe.PURPOSE)
            String purpose) {}

    record AuthorizationProbeResponse(UUID probeId, long version, Instant committedAt) {}
}
