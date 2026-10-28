package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class PromptRulesTest {

    private static final Path MAIN_YML = Path.of("src/main/resources/application.yml");
    private static final Path TEST_YML = Path.of("src/test/resources/application.yml");

    private static PromptRules with(String chat, String transform, String tools, String finalRound, String correction) {
        return new PromptRules(chat, transform, tools, null, finalRound, correction);
    }

    private static PromptRules withRouting(String routing) {
        return new PromptRules(null, null, null, routing, null, null);
    }

    private static PromptRules bind(Path yml) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("yml", new FileSystemResource(yml))) {
            sources.addLast(source);
        }
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("rag.prompts", PromptRules.class).orElseGet(PromptRules::defaults);
    }

    @Test
    void defaultsAreThePromptsConstants() {
        PromptRules d = PromptRules.defaults();
        assertThat(d.chatSystem()).isEqualTo(Prompts.CHAT_SYSTEM);
        assertThat(d.transformSystem()).isEqualTo(Prompts.TRANSFORM_SYSTEM);
        assertThat(d.toolRules()).isEqualTo(Prompts.TOOL_RULES);
        assertThat(d.toolRouting()).isEqualTo(Prompts.TOOL_ROUTING);
        assertThat(d.finalRoundRule()).isEqualTo(Prompts.FINAL_ROUND_RULE);
        assertThat(d.correction()).isEqualTo(Prompts.CORRECTION);
    }

    @Test
    void blankValueFailsNamingTheKey() {
        assertThatThrownBy(() -> with("  ", null, null, null, null)).hasMessageContaining("rag.prompts.chat-system");
        assertThatThrownBy(() -> with(null, "  ", null, null, null)).hasMessageContaining("rag.prompts.transform-system");
        assertThatThrownBy(() -> with(null, null, "  ", null, null)).hasMessageContaining("rag.prompts.tool-rules");
        assertThatThrownBy(() -> withRouting("  ")).hasMessageContaining("rag.prompts.tool-routing");
        assertThatThrownBy(() -> with(null, null, null, "  ", null)).hasMessageContaining("rag.prompts.final-round-rule");
        assertThatThrownBy(() -> with(null, null, null, null, "  ")).hasMessageContaining("rag.prompts.correction");
    }

    @Test
    void chatSystemWithoutNoAnswerFails() {
        assertThatThrownBy(() -> with("answer anything", null, null, null, null)).hasMessageContaining("NO_ANSWER");
    }

    @Test
    void correctionNeedsExactlyOnePlaceholder() {
        assertThatThrownBy(() -> with(null, null, null, null, "no placeholder")).hasMessageContaining("%s");
        assertThatThrownBy(() -> with(null, null, null, null, "%s and %s")).hasMessageContaining("%s");
    }

    @Test
    void hashIsTwelveHexStableAndSensitiveToEveryText() {
        PromptRules d = PromptRules.defaults();
        assertThat(d.hash()).matches("[0-9a-f]{12}").isEqualTo(PromptRules.defaults().hash());
        String chat = Prompts.CHAT_SYSTEM + "x";
        assertThat(with(chat, null, null, null, null).hash()).isNotEqualTo(d.hash());
        assertThat(with(null, Prompts.TRANSFORM_SYSTEM + "x", null, null, null).hash()).isNotEqualTo(d.hash());
        assertThat(with(null, null, Prompts.TOOL_RULES + "x", null, null).hash()).isNotEqualTo(d.hash());
        assertThat(withRouting(Prompts.TOOL_ROUTING + "x").hash()).isNotEqualTo(d.hash());
        assertThat(with(null, null, null, Prompts.FINAL_ROUND_RULE + "x", null).hash()).isNotEqualTo(d.hash());
        assertThat(with(null, null, null, null, Prompts.CORRECTION + "x").hash()).isNotEqualTo(d.hash());
    }

    @Test
    void mainYmlBindsByteIdenticalToDefaults() throws IOException {
        PromptRules bound = bind(MAIN_YML);
        assertThat(bound).isEqualTo(PromptRules.defaults());
        assertThat(bound.hash()).isEqualTo(PromptRules.defaults().hash());
    }

    @Test
    void testYmlCarriesNoPromptsBlockAndYieldsDefaults() throws IOException {
        assertThat(bind(TEST_YML)).isEqualTo(PromptRules.defaults());
    }
}
