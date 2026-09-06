package com.dev.turkim.youtubemcp.config;

import com.dev.turkim.youtubemcp.service.YoutubeSearchService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider youtubeToolCallbacks(YoutubeSearchService youtubeSearchService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(youtubeSearchService)
                .build();
    }
}
