package com.evision.releasemgmt;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.archfixture.IndexWritingChat;
import com.evision.releasemgmt.archfixture.JiraReachingTool;
import com.evision.releasemgmt.archfixture.MutatingTool;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.JiraTicketPort;
import com.evision.releasemgmt.ports.PromotionRepository;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.RoadmapHistoryRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                        "..adapters.render..", "..adapters.rag..")
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

    private static final Set<String> CREDENTIAL_READERS = Set.of(
            "com.evision.releasemgmt.application.AuthenticationService",
            "com.evision.releasemgmt.application.JiraAccessContextService",
            "com.evision.releasemgmt.application.briefing.BriefingJiraAccess");

    @Test
    void onlyTheAuthenticationAndAccessServicesUseTheCredentialPort() {
        DescribedPredicate<JavaClass> credentialReader = DescribedPredicate.describe("an allowlisted credential reader",
                javaClass -> CREDENTIAL_READERS.contains(javaClass.getName())
                        || CREDENTIAL_READERS.stream().anyMatch(name -> javaClass.getName().startsWith(name + "$")));
        noClasses()
                .that().resideOutsideOfPackage("..adapters.persistence..")
                .and(DescribedPredicate.not(credentialReader))
                .and().doNotHaveFullyQualifiedName("com.evision.releasemgmt.ports.JiraCredentialRepository")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.evision.releasemgmt.ports.JiraCredentialRepository")
                .check(classes);
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
    void roadmapHistoryRepositoryExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(RoadmapHistoryRepository.class)
                .should().haveNameMatching("(update|delete|remove|save).*")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void clientDeploymentRepositoryExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(ClientDeploymentRepository.class)
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

    private static final String APPLICATION = "com.evision.releasemgmt.application";
    private static final String TOOLS_PACKAGE = APPLICATION + ".assistant.tools";

    private static final Set<String> READ_ONLY_CALLS = Set.of(
            "ReleaseService.get", "ReleaseService.search", "ReleaseService.releasesContaining",
            "PromotionService.history", "EnvironmentLadderService.ladder",
            "DashboardService.releases", "DashboardService.deployments", "DashboardService.ticketChurn",
            "DocumentReadinessService.all", "DocumentGenerationService.revisions",
            "ActorResolutionService.resolve", "PortfolioDashboardService.matrix");

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "ReleaseService", "PromotionService", "EnvironmentLadderService", "DashboardService",
            "DocumentReadinessService", "DocumentGenerationService", "ActorResolutionService", "ResolvedActors",
            "PortfolioDashboardService")
            .stream().map(name -> APPLICATION + "." + name).collect(Collectors.toUnmodifiableSet());

    private static final Set<String> INDEX_WRITER_SIMPLE_NAMES = Set.of(
            "RagIndexPort", "IndexSyncer", "IndexSweep", "IndexInvalidationListener", "IndexDocumentMapper");

    private static final Set<String> CHAT_PATH_SIMPLE_NAMES = Set.of(
            "AssistantService", "AssistantChatController", "BrowserStreamListener", "AssistantStatusController");

    private static final DescribedPredicate<JavaClass> REAL_CHAT_PATH = DescribedPredicate.describe(
            "the chat path (AssistantService, AssistantChatController, BrowserStreamListener, AssistantStatusController)",
            c -> CHAT_PATH_SIMPLE_NAMES.contains(c.getSimpleName()));

    private static final DescribedPredicate<JavaClass> FIXTURE_CHAT_PATH = DescribedPredicate.describe(
            "the fixture chat path", c -> c.getSimpleName().equals("IndexWritingChat"));

    private static boolean isAllowedServiceOrNestedData(JavaClass type) {
        String name = type.getName();
        return ALLOWED_SERVICES.stream().anyMatch(service -> name.equals(service) || name.startsWith(service + "$"));
    }

    private static final DescribedPredicate<JavaClass> ALLOWED_TOOL_DEPENDENCY = DescribedPredicate.describe(
            "an allowlisted JDK, framework, domain, tools-package or read-service type",
            type -> type.getPackageName().startsWith("java.")
                    || type.getPackageName().startsWith("org.slf4j")
                    || type.getPackageName().startsWith("tools.jackson")
                    || type.getPackageName().startsWith("com.fasterxml.jackson.annotation")
                    || type.getPackageName().startsWith("org.springframework.stereotype")
                    || type.getPackageName().startsWith("org.springframework.boot.autoconfigure.condition")
                    || type.getPackageName().startsWith("com.evision.releasemgmt.domain")
                    || type.getPackageName().equals(TOOLS_PACKAGE)
                    || isAllowedServiceOrNestedData(type));

    static ArchCondition<JavaClass> onlyCallAllowlistedReadMethods() {
        return new ArchCondition<>("only call allowlisted read methods on application services") {
            @Override
            public void check(JavaClass tool, ConditionEvents events) {
                for (JavaMethodCall call : tool.getMethodCallsFromSelf()) {
                    JavaClass owner = call.getTargetOwner();
                    boolean applicationService = owner.getPackageName().startsWith(APPLICATION)
                            && !owner.getPackageName().startsWith(TOOLS_PACKAGE)
                            && !owner.isRecord()
                            && !owner.isNestedClass();
                    if (applicationService && !READ_ONLY_CALLS.contains(owner.getSimpleName() + "." + call.getName())) {
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
            }
        };
    }

    static ArchRule toolsCallOnlyAllowlistedReadMethods() {
        return classes().that().implement(AssistantTool.class)
                .should(onlyCallAllowlistedReadMethods());
    }

    static ArchRule toolsDependOnlyOnAllowedTypes() {
        return classes().that().implement(AssistantTool.class)
                .should().onlyDependOnClassesThat(ALLOWED_TOOL_DEPENDENCY);
    }

    static ArchRule chatPathCannotWriteTheIndex(DescribedPredicate<JavaClass> chatPath) {
        return noClasses().that(chatPath)
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "an index writer", type -> INDEX_WRITER_SIMPLE_NAMES.contains(type.getSimpleName())));
    }

    @Test
    void toolsCallOnlyAllowlistedReadMethodsOnTheRealClasses() {
        toolsCallOnlyAllowlistedReadMethods().check(classes);
    }

    @Test
    void toolsDependOnlyOnAllowedTypesOnTheRealClasses() {
        toolsDependOnlyOnAllowedTypes().check(classes);
    }

    @Test
    void chatPathCannotWriteTheIndexOnTheRealClasses() {
        chatPathCannotWriteTheIndex(REAL_CHAT_PATH).check(classes);
    }

    @Test
    void transformPathCannotWriteTheIndexOnTheRealClasses() {
        chatPathCannotWriteTheIndex(DescribedPredicate.describe("the transform path",
                c -> Set.of("AssistantTransformController", "AssistantTransformService").contains(c.getSimpleName())))
                .check(classes);
    }

    @Test
    void toolRulesApplyToTheRealTools() {
        Set<String> tools = classes.stream()
                .filter(c -> c.isAssignableTo(AssistantTool.class) && !c.isInterface())
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());
        assertThat(tools).containsExactlyInAnyOrder(
                "ReleasesTool", "TicketsTool", "DeploymentsTool", "DashboardTool", "DocumentsTool", "InstallsTool");
        classes().that().implement(AssistantTool.class)
                .should().resideInAPackage(TOOLS_PACKAGE)
                .check(classes);
    }

    @Test
    void toolRulesRejectViolations() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(
                MutatingTool.class, JiraReachingTool.class, IndexWritingChat.class,
                ReleaseService.class, JiraTicketPort.class, RagIndexPort.class, AssistantTool.class);

        assertThatThrownBy(() -> toolsCallOnlyAllowlistedReadMethods().check(fixtures))
                .isInstanceOf(AssertionError.class).hasMessageContaining("freeze");
        assertThatThrownBy(() -> toolsDependOnlyOnAllowedTypes().check(fixtures))
                .isInstanceOf(AssertionError.class).hasMessageContaining("JiraTicketPort");
        assertThatThrownBy(() -> chatPathCannotWriteTheIndex(FIXTURE_CHAT_PATH).check(fixtures))
                .isInstanceOf(AssertionError.class).hasMessageContaining("RagIndexPort");
    }

    @Test
    void toolsAreNotTransactional() {
        ArchRule classRule = noClasses().that().implement(AssistantTool.class)
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);
        classRule.check(classes);
        ArchRule methodRule = ArchRuleDefinition.noMethods()
                .that().areDeclaredInClassesThat().implement(AssistantTool.class)
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .allowEmptyShould(true);
        methodRule.check(classes);
    }

    @Test
    void ragClientTypesStayInTheRagAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..adapters..", "..config..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.client.RestClient")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void ragChatPortExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(RagChatPort.class)
                .should().haveNameMatching("(update|delete|remove|save|index|upsert|write|put|add).*")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void briefingPortExposesNoMutation() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredIn(JiraBriefingPort.class)
                .should().haveNameMatching("(update|delete|remove|save|create|post|put|add|transition|comment|write).*")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void theWebAdapterCannotReachTheJiraBriefingPort() {
        noClasses().that().resideInAPackage("..adapters.web..").should().dependOnClassesThat()
                .haveFullyQualifiedName("com.evision.releasemgmt.ports.JiraBriefingPort")
                .allowEmptyShould(true).check(classes);
    }
}
