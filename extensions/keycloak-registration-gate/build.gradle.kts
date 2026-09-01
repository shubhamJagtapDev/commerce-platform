import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    alias(libs.plugins.error.prone)
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    compileOnly(libs.jspecify)
    compileOnly("org.keycloak:keycloak-core:26.7.0")
    compileOnly("org.keycloak:keycloak-server-spi:26.7.0")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.7.0")
    compileOnly("org.keycloak:keycloak-services:26.7.0")
    errorprone(libs.error.prone.core)
    errorprone(libs.nullaway)

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        target("src/*/java/**/*.java")
    }
}

tasks.named("spotlessJava") {
    inputs.property("javaRelease", 21)
    mustRunAfter(
        ":services:catalog-service:spotlessJava",
        ":services:identity-access-service:spotlessJava",
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        check("NullAway", CheckSeverity.ERROR)
        error("StringSplitter")
        option("NullAway:AnnotatedPackages", "com.commerce")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

dependencyLocking {
    lockAllConfigurations()
}
