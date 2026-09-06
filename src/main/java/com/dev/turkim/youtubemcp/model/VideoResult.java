package com.dev.turkim.youtubemcp.model;

/**
 * The shape returned to the MCP client for each ranked video.
 *
 * @param likeToViewRatio raw likes / views for this video alone
 * @param weightedRating  the final ranking score: likeToViewRatio adjusted by view count as
 *                        a confidence weight (see YoutubeSearchService#rankByViewWeightedRating) —
 *                        this is what the result list is sorted by
 */
public record VideoResult(
        String videoId,
        String title,
        String channelTitle,
        String url,
        String publishedAt,
        long viewCount,
        long likeCount,
        double likeToViewRatio,
        double weightedRating
) {}