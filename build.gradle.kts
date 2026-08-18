plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.commerce"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
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
}
