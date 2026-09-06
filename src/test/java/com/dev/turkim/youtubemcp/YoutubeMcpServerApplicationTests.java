package com.dev.turkim.youtubemcp;

import com.dev.turkim.youtubemcp.config.YoutubeProperties;
import com.dev.turkim.youtubemcp.service.ToolDescriptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the application context wires up, the configuration properties bind, and both
 * MCP tools are exposed under the exact names that clients bind to. A broken {@code @Tool}
 * annotation or a missing bean fails here rather than at runtime against a live client.
 */
@SpringBootTest
class YoutubeMcpServerApplicationTests {

    @Autowired
    @Qualifier("youtubeToolCallbacks")
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    private YoutubeProperties youtubeProperties;

    @Test
    void bothMcpToolsAreExposedUnderTheirContractNames() {
        assertThat(toolCallbackProvider.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        ToolDescriptions.BY_CATEGORY_NAME,
                        ToolDescriptions.BY_TOPIC_NAME);
    }

    @Test
    void toolDescriptionsAreNonEmptySoClientsCanTellTheTwoToolsApart() {
        assertThat(toolCallbackProvider.getToolCallbacks())
                .allSatisfy(callback ->
                        assertThat(callback.getToolDefinition().description()).isNotBlank());
    }

    @Test
    void youtubePropertiesBindFromConfiguration() {
        assertThat(youtubeProperties.key()).isNotBlank();
        assertThat(youtubeProperties.baseUrl()).isEqualTo("https://www.googleapis.com/youtube/v3");
    }
}
