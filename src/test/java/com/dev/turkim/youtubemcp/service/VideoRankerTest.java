package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.model.VideoResult;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoItem;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoSnippet;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoStatistics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VideoRankerTest {

    private static VideoItem video(String id, long views, long likes) {
        return new VideoItem(
                id,
                new VideoSnippet("title-" + id, "channel-" + id, "2024-01-01T00:00:00Z", "desc"),
                new VideoStatistics(String.valueOf(views), String.valueOf(likes), "0"));
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(VideoRanker.rankByViewWeightedRating(List.of(), 10)).isEmpty();
    }

    @Test
    void limitsResultsToTopN() {
        List<VideoItem> items = List.of(
                video("a", 1000, 100),
                video("b", 2000, 150),
                video("c", 3000, 90));

        assertThat(VideoRanker.rankByViewWeightedRating(items, 2)).hasSize(2);
    }

    @Test
    void lowViewVideosHaveTheirRatioDampedTowardThePoolAverage() {
        // "b" has a 100% like ratio on 10 views; "a" is genuinely popular at 5%.
        // The damping pulls b's headline ratio down to roughly the pool average, so a
        // fluke can never present itself to the client as a 100%-rated video.
        List<VideoItem> items = List.of(
                video("a", 10_000_000, 500_000), // 5% ratio, huge audience
                video("b", 10, 10));             // 100% ratio, 10 views

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(items, 10);
        VideoResult fluke = ranked.stream()
                .filter(r -> r.videoId().equals("b")).findFirst().orElseThrow();

        assertThat(fluke.likeToViewRatio()).isEqualTo(1.0);            // raw ratio untouched
        assertThat(fluke.weightedRating()).isCloseTo(0.05, within(0.001)); // damped ~20x
        assertThat(fluke.weightedRating()).isLessThan(fluke.likeToViewRatio() / 10);
    }

    /**
     * Characterisation test for a KNOWN DEFECT — this pins current behaviour, it does not
     * endorse it. The 10-view fluke takes first place over a 10M-view video.
     *
     * <p>Cause: {@code rankByViewWeightedRating} shrinks every score toward
     * {@code C = totalLikes / totalViews}. That average is view-weighted, so the high-view
     * videos define C and can never sit meaningfully above it. Ordering then reduces to
     * {@code weight × (ratio − C)}: the fluke scores {@code 2.0e-06 × 0.95 = +1.9e-06}
     * while the popular video scores {@code 0.67 × −6e-07 = −3.3e-07}. The damping never
     * fires, and any low-view video with an above-average ratio wins by an epsilon.
     *
     * <p>Real impact is on {@code search_youtube_top_videos_by_topic}, where keyword search
     * can surface obscure videos. The category tool is mostly shielded, because
     * {@code chart=mostPopular} only returns videos with substantial view counts.
     *
     * <p>WHEN THE RANKER IS FIXED, THIS TEST SHOULD FAIL — delete it and assert the real
     * guarantee (the popular video ranks first) instead.
     */
    @Test
    void knownDefect_lowViewFlukeEdgesAheadOfGenuinelyPopularVideo() {
        List<VideoItem> items = List.of(
                video("a", 10_000_000, 500_000),
                video("b", 10, 10));

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(items, 10);

        assertThat(ranked).extracting(VideoResult::videoId).containsExactly("b", "a");
        // ...and it wins by a margin far below any meaningful difference in quality:
        assertThat(ranked.getFirst().weightedRating() - ranked.getLast().weightedRating())
                .isLessThan(1e-5);
    }

    @Test
    void amongComparablyPopularVideosBetterRatioWins() {
        List<VideoItem> items = List.of(
                video("low", 1_000_000, 10_000),   // 1%
                video("high", 1_000_000, 80_000)); // 8%

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(items, 10);

        assertThat(ranked.getFirst().videoId()).isEqualTo("high");
    }

    @Test
    void missingStatisticsAreTreatedAsZeroRatherThanFailing() {
        VideoItem noStats = new VideoItem("x", null, null);

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(List.of(noStats), 10);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().viewCount()).isZero();
        assertThat(ranked.getFirst().likeCount()).isZero();
        assertThat(ranked.getFirst().likeToViewRatio()).isZero();
        assertThat(ranked.getFirst().title()).isEqualTo("(untitled)");
    }

    @Test
    void resultsCarryUrlAndPreserveRawRatioAlongsideWeightedRating() {
        List<VideoItem> items = List.of(video("abc", 1000, 100), video("def", 5000, 250));

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(items, 10);

        VideoResult first = ranked.getFirst();
        assertThat(first.url()).isEqualTo("https://www.youtube.com/watch?v=" + first.videoId());
        // raw ratio stays untouched; weighted rating is the derived sort key
        assertThat(first.likeToViewRatio()).isEqualTo((double) first.likeCount() / first.viewCount());
        assertThat(first.weightedRating()).isBetween(0.0, 1.0);
    }

    @Test
    void everyWeightedRatingStaysWithinZeroToOne() {
        List<VideoItem> items = List.of(
                video("a", 10, 10),
                video("b", 5_000, 1_000),
                video("c", 100_000_000, 9_000_000));

        assertThat(VideoRanker.rankByViewWeightedRating(items, 10))
                .allSatisfy(r -> assertThat(r.weightedRating()).isBetween(0.0, 1.0));
    }

    @Test
    void resultsAreOrderedByTheWeightedRatingTheyReport() {
        List<VideoItem> items = List.of(
                video("a", 10, 10),
                video("b", 1_000_000, 10_000),
                video("c", 250_000, 40_000),
                video("d", 8_000_000, 100_000));

        List<VideoResult> ranked = VideoRanker.rankByViewWeightedRating(items, 10);

        assertThat(ranked).isSortedAccordingTo(
                java.util.Comparator.comparingDouble(VideoResult::weightedRating).reversed());
    }
}
