package com.evision.releasemgmt;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
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
                .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..", "tools.jackson..")
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
        // adapters subpackage (jira/persistence/web/render) remains off-limits;
        // RenderService can only reach the render engine through RenderEnginePort.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapters.jira..", "..adapters.persistence..", "..adapters.web..",
                        "..adapters.render..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void renderLibrariesLiveOnlyInTheRenderAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..adapters.render..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.samskivert..", "com.vladsch..",
                        "com.openhtmltopdf..", "org.docx4j..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * The display path must not be able to reach the token-bearing port. Loading JiraCredential
     * decrypts encrypted_token, so a controller that resolved an actor through findByAccountId would
     * let one row encrypted under a superseded RM_CREDENTIAL_KEY take down the document, release and
     * template screens (D-05.3-03). ActorDirectory exists so this is a compile-time impossibility
     * rather than a convention; these rules stop the convention being quietly abandoned.
     */
    @Test
    void theWebAdapterCannotReachTheTokenBearingCredentialPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapters.web..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.evision.releasemgmt.ports.JiraCredentialRepository")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void theWebAdapterCannotReachTheTokenBearingAdoCredentialPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapters.web..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.evision.releasemgmt.ports.AdoCredentialRepository")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void actorResolutionResolvesThroughActorDirectoryOnly() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("ActorResolutionService")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.evision.releasemgmt.ports.JiraCredentialRepository")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void promotionRepositoryExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(PromotionRepository.class)
                .should().haveNameMatching("(update|delete|remove|save).*")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void dashboardReadRepositoryExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(DashboardReadRepository.class)
                .should().haveNameMatching("(update|delete|remove|save).*")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
