package com.evision.releasemgmt;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureBoundaryTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.evision.releasemgmt");

    @Test
    void domainAndPortsHaveNoFrameworkImports() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..", "..ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void domainAndPortsDoNotDependOnAdaptersConfigOrApplication() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..", "..ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapters..", "..config..", "..application..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        // adapters.security is exempted: it is the session/token infrastructure
        // (JwtTokenService) that application/AuthenticationService orchestrates
        // directly, per plan 06 - there is no port abstraction over it and none
        // is warranted for a single-implementation technical concern. Every other
        // adapters subpackage (jira/persistence/web) remains off-limits.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapters.jira..", "..adapters.persistence..", "..adapters.web..")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
