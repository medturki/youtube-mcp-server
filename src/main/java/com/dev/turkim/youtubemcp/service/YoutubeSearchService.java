package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.client.YoutubeApiClient;
import com.dev.turkim.youtubemcp.model.VideoResult;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoItem;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * MCP tool entry points. Orchestration only: resolve inputs, delegate the HTTP calls to
 * {@link YoutubeApiClient}, and the ordering to {@link VideoRanker}. Tool names and
 * description text live in {@link ToolDescriptions}.
 */
@Service
public class YoutubeSearchService {

    private static final int CANDIDATE_POOL_SIZE = 25; // fetched before re-ranking
    private static final int TOP_N = 10;

    private final YoutubeApiClient apiClient;
    private final CategoryResolver categoryResolver;

    public YoutubeSearchService(YoutubeApiClient apiClient, CategoryResolver categoryResolver) {
        this.apiClient = apiClient;
        this.categoryResolver = categoryResolver;
    }

    @Tool(name = ToolDescriptions.BY_CATEGORY_NAME, description = ToolDescriptions.BY_CATEGORY)
    public List<VideoResult> searchTopVideosByCategory(
            @ToolParam(description = ToolDescriptions.CATEGORY_PARAM)
            String category,
            @ToolParam(description = ToolDescriptions.REGION_PARAM_CATEGORY, required = false)
            String regionCode
    ) {
        String region = normalizeRegionCode(regionCode);
        String categoryId = categoryResolver.resolve(category, region);

        List<VideoItem> candidates =
                apiClient.listMostPopularByCategory(categoryId, region, CANDIDATE_POOL_SIZE);

        return VideoRanker.rankByViewWeightedRating(candidates, TOP_N);
    }

    @Tool(name = ToolDescriptions.BY_TOPIC_NAME, description = ToolDescriptions.BY_TOPIC)
    public List<VideoResult> searchTopVideosByTopic(
            @ToolParam(description = ToolDescriptions.TOPIC_PARAM)
            String topic,
            @ToolParam(description = ToolDescriptions.REGION_PARAM_TOPIC, required = false)
            String regionCode
    ) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        String region = (regionCode == null || regionCode.isBlank()) ? null : normalizeRegionCode(regionCode);

        List<String> videoIds = apiClient.searchVideoIds(topic, region, CANDIDATE_POOL_SIZE);
        List<VideoItem> details = apiClient.listVideosByIds(videoIds);

        return VideoRanker.rankByViewWeightedRating(details, TOP_N);
    }

    /**
     * Defends against blank input and against non-ISO junk that some MCP clients send for
     * unset optional string parameters (e.g. the literal placeholder text "string"), which
     * would otherwise reach the YouTube API and come back as an opaque 400 invalidRegionCode.
     */
    private String normalizeRegionCode(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return "US";
        }
        String trimmed = regionCode.trim();
        if (!trimmed.matches("(?i)[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "regionCode must be a 2-letter ISO 3166-1 alpha-2 code (e.g. 'US', 'FR'), got: '" + regionCode + "'");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}