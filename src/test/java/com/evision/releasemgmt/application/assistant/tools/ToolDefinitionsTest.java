package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.DocumentReadinessService;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ReleaseService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolDefinitionsTest {

    // Phase 11 intake limits: rag-service/src/main/resources/application.yml rag.tools
    private static final int MAX_TOOLS = 5;
    private static final int DEFINITIONS_MAX_CHARS = 1500;
    private static final int DESCRIPTION_MAX_CHARS = 300;
    private static final Set<String> SCHEMA_KEYS = Set.of("type", "properties", "required", "additionalProperties");
    private static final Set<String> PROPERTY_KEYS = Set.of("type", "description", "enum", "maxLength");
    private static final Set<String> PROPERTY_TYPES = Set.of("string", "integer", "number", "boolean");

    private final JsonMapper mapper = JsonMapper.builder().build();

    private List<AssistantTool> tools() {
        ReleaseService releaseService = mock(ReleaseService.class);
        PromotionService promotionService = mock(PromotionService.class);
        DashboardService dashboardService = mock(DashboardService.class);
        EnvironmentLadderService ladder = mock(EnvironmentLadderService.class);
        ActorResolutionService actors = mock(ActorResolutionService.class);
        ReleaseRef releaseRef = mock(ReleaseRef.class);
        return List.of(
                new ReleasesTool(releaseService, promotionService, releaseRef),
                new DashboardTool(dashboardService, ladder),
                new TicketsTool(releaseService, dashboardService, releaseRef),
                new DeploymentsTool(promotionService, dashboardService, ladder, actors, releaseRef, Clock.systemUTC()),
                new DocumentsTool(mock(DocumentReadinessService.class), mock(DocumentGenerationService.class), actors,
                        releaseService, releaseRef));
    }

    private String serialised(AssistantTool tool) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", tool.name());
        definition.put("description", tool.description());
        definition.put("parameters", tool.parameters());
        return mapper.writeValueAsString(definition);
    }

    @Test
    void fiveUniquelyNamedToolsFitThePhase11Budget() {
        List<AssistantTool> tools = tools();

        assertThat(tools).hasSize(MAX_TOOLS);
        assertThat(tools.stream().map(AssistantTool::name).collect(Collectors.toSet())).hasSize(MAX_TOOLS);
        tools.forEach(t -> assertThat(t.description().length()).isLessThanOrEqualTo(DESCRIPTION_MAX_CHARS));
        int total = tools.stream().mapToInt(t -> serialised(t).length()).sum();
        assertThat(total).isLessThanOrEqualTo(DEFINITIONS_MAX_CHARS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everySchemaStaysInsideTheFlatSubset() {
        for (AssistantTool tool : tools()) {
            Map<String, Object> parameters = tool.parameters();
            assertThat(SCHEMA_KEYS).containsAll(parameters.keySet());
            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) parameters.get("properties");
            properties.values().forEach(property -> {
                assertThat(PROPERTY_KEYS).containsAll(property.keySet());
                assertThat(PROPERTY_TYPES).contains((String) property.get("type"));
            });
        }
    }
}
