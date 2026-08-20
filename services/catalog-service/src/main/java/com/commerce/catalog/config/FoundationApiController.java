package com.commerce.catalog.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping(path = "/api/{version}/foundation", version = "1.0")
@Tag(name = "Development foundation")
public class FoundationApiController {

    @GetMapping
    @Operation(
            summary = "Verify the versioned Catalog HTTP foundation",
            description = "Development-only probe; absent from staging and production profiles.")
    @ApiResponse(responseCode = "200", description = "Versioned MVC mapping is active.")
    FoundationStatus foundation(@PathVariable String version) {
        return new FoundationStatus("catalog-service", version, "READY");
    }

    @Schema(description = "Development-only versioning probe response.")
    record FoundationStatus(
            @Schema(example = "catalog-service") String service,
            @Schema(example = "v1") String apiVersion,
            @Schema(example = "READY") String state) {}
}
