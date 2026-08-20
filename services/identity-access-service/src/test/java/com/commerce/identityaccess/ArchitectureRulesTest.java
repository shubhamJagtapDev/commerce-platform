package com.commerce.identityaccess;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureRulesTest {

    @Test
    void domainCodeDoesNotDependOnWebOrPersistenceFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "org.hibernate..")
                .allowEmptyShould(true)
                .check(applicationClasses());
    }

    @Test
    void controllersDoNotDependDirectlyOnJpaRepositories() {
        noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(JpaRepository.class)
                .check(applicationClasses());
    }

    private static JavaClasses applicationClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.commerce.identityaccess");
    }
}
