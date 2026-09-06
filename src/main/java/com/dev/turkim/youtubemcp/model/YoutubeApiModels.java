package com.dev.turkim.youtubemcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal, ignore-unknown-fields DTOs for the two YouTube Data API v3 endpoints we call:
 * "search.list" and "videos.list". We only map the fields we actually need.
 */
public class YoutubeApiModels {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryListResponse(List<CategoryItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryItem(String id, CategorySnippet snippet) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySnippet(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchListResponse(List<SearchItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchItem(SearchItemId id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchItemId(String videoId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoListResponse(List<VideoItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoItem(String id, VideoSnippet snippet, VideoStatistics statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoSnippet(String title, String channelTitle, String publishedAt, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoStatistics(String viewCount, String likeCount, String commentCount) {}
}
