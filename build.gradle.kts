plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.error.prone) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "com.commerce"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

spotless {
    format("misc") {
        target(
            ".github/**/*.yml",
            "*.gradle.kts",
            "gradle/*.toml",
            "scripts/**/*.sh",
            "dev")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register<Exec>("architectureCheck") {
    group = "verification"
    description = "Checks monorepo service and database ownership boundaries."
    commandLine("bash", "scripts/verify-monorepo-boundaries.sh")
}

tasks.register<Exec>("contractCheck") {
    group = "verification"
    description = "Validates versioned HTTP and event contract sources."
    commandLine("bash", "scripts/verify-contracts.sh")
}

tasks.register<Exec>("secretCheck") {
    group = "verification"
    description = "Rejects committed-looking secrets and local overrides."
    commandLine("bash", "scripts/verify-no-secrets.sh")
}

tasks.named("check") {
    dependsOn("architectureCheck", "contractCheck", "secretCheck")
    dependsOn(
        ":services:catalog-service:check",
        ":services:identity-access-service:check",
        ":extensions:keycloak-registration-gate:check")
}
