package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.model.VideoResult;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoItem;

import java.util.Comparator;
import java.util.List;

/**
 * Pure ranking logic: maps YouTube API items to {@link VideoResult} and orders them by a
 * view-weighted rating. No Spring, no HTTP, no state — every method is a function of its
 * arguments, which makes this the easiest piece of the system to test.
 */
public final class VideoRanker {

    private VideoRanker() {
    }

    /**
     * Ranks videos by a Bayesian-style "weighted rating" that uses each video's view count
     * as a confidence weight on its like/view ratio:
     *
     * <pre>
     *   weightedRating = (views / (views + m)) * ratio + (m / (views + m)) * poolAvgRatio
     * </pre>
     *
     * where m (the damping constant) is the average view count across the candidate pool.
     * A video with far more views than average has its own ratio dominate the score; a
     * video with very few views gets pulled toward the pool's overall average ratio, so it
     * can't rank #1 purely on a tiny number of lucky likes.
     *
     * @param videos  the candidate pool
     * @param topN    how many results to return
     */
    public static List<VideoResult> rankByViewWeightedRating(List<VideoItem> videos, int topN) {
        List<VideoResult> results = videos.stream().map(VideoRanker::toVideoResult).toList();
        if (results.isEmpty()) {
            return List.of();
        }

        long totalViews = results.stream().mapToLong(VideoResult::viewCount).sum();
        long totalLikes = results.stream().mapToLong(VideoResult::likeCount).sum();
        double poolAvgRatio = totalViews == 0 ? 0.0 : (double) totalLikes / totalViews;
        double dampingConstant = (double) totalViews / results.size(); // avg views in the pool

        return results.stream()
                .map(r -> withWeightedRating(r, poolAvgRatio, dampingConstant))
                .sorted(Comparator.comparingDouble(VideoResult::weightedRating).reversed())
                .limit(topN)
                .toList();
    }

    static VideoResult withWeightedRating(VideoResult r, double poolAvgRatio, double dampingConstant) {
        double views = r.viewCount();
        double denominator = views + dampingConstant;
        double weight = denominator == 0 ? 0.0 : views / denominator;
        double weightedRating = weight * r.likeToViewRatio() + (1 - weight) * poolAvgRatio;

        return new VideoResult(
                r.videoId(), r.title(), r.channelTitle(), r.url(), r.publishedAt(),
                r.viewCount(), r.likeCount(), r.likeToViewRatio(), weightedRating
        );
    }

    static VideoResult toVideoResult(VideoItem item) {
        long views = parseLongSafe(item.statistics() != null ? item.statistics().viewCount() : null);
        long likes = parseLongSafe(item.statistics() != null ? item.statistics().likeCount() : null);
        double ratio = views == 0 ? 0.0 : (double) likes / views;

        return new VideoResult(
                item.id(),
                item.snippet() != null ? item.snippet().title() : "(untitled)",
                item.snippet() != null ? item.snippet().channelTitle() : "(unknown channel)",
                "https://www.youtube.com/watch?v=" + item.id(),
                item.snippet() != null ? item.snippet().publishedAt() : null,
                views,
                likes,
                ratio,
                ratio // placeholder; replaced by withWeightedRating() once the pool average is known
        );
    }

    private static long parseLongSafe(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}