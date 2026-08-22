package com.commerce.identityaccess.config;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
