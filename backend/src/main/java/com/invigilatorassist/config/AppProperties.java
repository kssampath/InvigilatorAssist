package com.invigilatorassist.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Jwt jwt, Cors cors, @Valid Allocation allocation, Dev dev) {

    /** HS256 signing secret: at least 32 characters, injected from Key Vault in Azure. */
    public record Jwt(@NotBlank @Size(min = 32) String secret, Duration ttl, String issuer) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Allocation(@Min(1) int studentsPerInvigilator) {}

    /** Only read by the dev profile's seeder. */
    public record Dev(String adminPassword) {}
}
