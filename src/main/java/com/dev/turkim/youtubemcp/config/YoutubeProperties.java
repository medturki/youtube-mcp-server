package com.dev.turkim.youtubemcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube.api")
public record YoutubeProperties(String key, String baseUrl) {

    public YoutubeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.googleapis.com/youtube/v3";
        }
    }
}
