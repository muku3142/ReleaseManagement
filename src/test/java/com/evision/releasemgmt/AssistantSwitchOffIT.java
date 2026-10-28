package com.evision.releasemgmt;

import com.evision.releasemgmt.adapters.web.AssistantChatController;
import com.evision.releasemgmt.adapters.web.AssistantStatusController;
import com.evision.releasemgmt.adapters.web.AssistantTransformController;
import com.evision.releasemgmt.application.assistant.AssistantService;
import com.evision.releasemgmt.application.assistant.AssistantTransformService;
import com.evision.releasemgmt.application.assistant.IndexInvalidationListener;
import com.evision.releasemgmt.application.assistant.IndexSweep;
import com.evision.releasemgmt.application.assistant.IndexSyncer;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.application.assistant.tools.ToolRegistry;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.RagStatusPort;
import com.evision.releasemgmt.ports.RagTransformPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AssistantSwitchOffIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ReleaseRepository releases;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
    }

    @Test
    void switchOffConstructsNoAssistantBean() {
        List<Class<?>> assistantTypes = List.of(RagIndexPort.class, RagChatPort.class, IndexSyncer.class,
                IndexSweep.class, IndexInvalidationListener.class, AssistantService.class, ToolRegistry.class,
                AssistantTool.class, AssistantProperties.class, RagStatusPort.class, AssistantChatController.class);

        for (Class<?> type : assistantTypes) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).isEmpty();
        }
        assertThat(context.containsBean("ragIndexRestClient")).isFalse();
    }

    @Test
    void chatEndpointIsNotMapped() {
        RequestMappingHandlerMapping mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet()).noneMatch(info ->
                info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition().getPatternValues().contains("/api/assistant/chat"));
    }

    @Test
    void switchOffConstructsNoTransformBeanAndMapsNoTransformEndpoint() {
        for (Class<?> type : List.of(AssistantTransformController.class, AssistantTransformService.class,
                RagTransformPort.class)) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).isEmpty();
        }
        RequestMappingHandlerMapping mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet()).noneMatch(info ->
                info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition().getPatternValues().stream()
                        .anyMatch(p -> p.equals("/api/assistant/transform")
                                || p.equals("/api/assistant/transform/limits")
                                || p.equals("/api/assistant/documents/ai-revisions")));
    }

    @Test
    void statusEndpointStillExistsAndReportsDisabled() {
        AssistantStatusController controller = context.getBean(AssistantStatusController.class);

        assertThat(controller.status().enabled()).isFalse();
        assertThat(controller.status().starters()).isNull();
        assertThat(controller.freshness().getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void briefingFallbackIsScheduledWithTheAssistantOff() {
        assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
                .flatMap(ScheduledTaskHolder::getScheduledTasks)
                .anyMatch(task -> task.getTask().toString().endsWith("BriefingScheduleService.scheduledTick"));
    }

    @Test
    void releaseMutationsStillWork() {
        Release created = releases.create(
                Release.create("Switch Off", "1.0", "CNV", "rm@acme.com", Instant.now()), List.of());
        ManualItem manual = ManualItem.create("Script", "desc", ManualItemCategory.OTHER, Instant.now());

        assertThat(releases.addManualItem(created.id(), manual).manualItems())
                .extracting(ManualItem::id).containsExactly(manual.id());
        assertThat(releases.removeManualItem(created.id(), manual.id()).manualItems()).isEmpty();
    }
}
