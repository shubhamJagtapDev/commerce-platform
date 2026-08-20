package com.commerce.catalog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Commerce Catalog API",
                        version = "v1",
                        description = "Catalog resource-server and lifecycle contracts."))
public class OpenApiConfiguration {}
