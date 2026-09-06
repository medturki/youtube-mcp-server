package com.dev.turkim.youtubemcp.client;

import com.dev.turkim.youtubemcp.config.YoutubeProperties;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * The only class that knows about YouTube Data API v3 endpoints and HTTP.
 * Returns raw API DTOs; all interpretation, caching and ranking lives elsewhere.
 */
@Component
public class YoutubeApiClient {

    private final RestClient restClient;
    private final YoutubeProperties properties;

    public YoutubeApiClient(RestClient youtubeRestClient, YoutubeProperties properties) {
        this.restClient = youtubeRestClient;
        this.properties = properties;
    }

    /** videoCategories.list — the official category taxonomy for a region. */
    public List<CategoryItem> listCategories(String region) {
        CategoryListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videoCategories")
                        .queryParam("part", "snippet")
                        .queryParam("regionCode", region)
                        .queryParam("key", properties.key())
                        .build())
                .retrieve()
                .body(CategoryListResponse.class);

        return response == null || response.items() == null ? List.of() : response.items();
    }

    /**
     * videos.list?chart=mostPopular — YouTube's purpose-built endpoint for top/trending
     * videos in a region and category. Preferred over search.list with order=viewCount +
     * videoCategoryId, which is unreliable (often empty) and costs 100 quota units vs. 1.
     * Returns snippet + statistics in a single call.
     */
    public List<VideoItem> listMostPopularByCategory(String categoryId, String region, int maxResults) {
        VideoListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("chart", "mostPopular")
                        .queryParam("videoCategoryId", categoryId)
                        .queryParam("regionCode", region)
                        .queryParam("maxResults", maxResults)
                        .queryParam("key", properties.key())
                        .build())
                .retrieve()
                .body(VideoListResponse.class);

        return response == null || response.items() == null ? List.of() : response.items();
    }

    /**
     * search.list?q=... — free-text keyword search, for topics that aren't one of YouTube's
     * fixed categories (e.g. "devops"). Pass a null region to search without restriction.
     * Returns video IDs only; call {@link #listVideosByIds} for snippet + statistics.
     */
    public List<String> searchVideoIds(String topic, String region, int maxResults) {
        SearchListResponse response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("q", topic)
                            .queryParam("order", "viewCount")
                            .queryParam("maxResults", maxResults)
                            .queryParam("key", properties.key());
                    if (region != null) {
                        uriBuilder.queryParam("regionCode", region);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(SearchListResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(SearchItem::id)
                .filter(id -> id != null && id.videoId() != null)
                .map(SearchItemId::videoId)
                .toList();
    }

    /** videos.list?id=... — snippet + statistics for a batch of known video IDs. */
    public List<VideoItem> listVideosByIds(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return List.of();
        }
        VideoListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("id", String.join(",", videoIds))
                        .queryParam("key", properties.key())
                        .build())
                .retrieve()
                .body(VideoListResponse.class);

        return response == null || response.items() == null ? List.of() : response.items();
    }
}