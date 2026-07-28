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
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapters..")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
