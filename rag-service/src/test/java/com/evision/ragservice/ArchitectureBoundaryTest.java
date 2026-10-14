package com.evision.ragservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.evision.ragservice");

    @Test
    void domainAndPortsHaveNoFrameworkImports() {
        noClasses().that().resideInAnyPackage("..domain..", "..ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml.jackson..", "tools.jackson..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainAndPortsDoNotDependOnAdaptersConfigOrApplication() {
        noClasses().that().resideInAnyPackage("..domain..", "..ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapters..", "..config..", "..application..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapters.web..", "..adapters.persistence..", "..adapters.ollama..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void jdbcLivesOnlyInThePersistenceAdapter() {
        noClasses().that().resideOutsideOfPackage("..adapters.persistence..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void onlyTheOllamaAdapterAndConfigMakeOutboundHttpCalls() {
        // SAFE-03: Ollama is the only outbound call
        noClasses().that().resideOutsideOfPackages("..adapters.ollama..", "..config..")
                .should().dependOnClassesThat().resideInAPackage("java.net.http..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void applicationNeverDependsOnTheServletLayer() {
        // keeps SseEmitter behind EventSink
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web.servlet..", "jakarta.servlet..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void restClientLivesOnlyInTheOllamaAdapterAndConfig() {
        noClasses().that().resideOutsideOfPackages("..adapters.ollama..", "..config..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.web.client..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
