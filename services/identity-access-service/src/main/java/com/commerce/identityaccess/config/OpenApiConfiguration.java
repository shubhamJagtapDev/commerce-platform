package com.commerce.identityaccess.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Commerce Identity Access API",
        version = "v1",
        description = "Same-origin BFF, customer account, and explicit downstream gateway contracts."))
public class OpenApiConfiguration {
}
