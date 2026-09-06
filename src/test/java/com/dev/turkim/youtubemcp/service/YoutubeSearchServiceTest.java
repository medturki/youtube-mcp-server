package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.client.YoutubeApiClient;
import com.dev.turkim.youtubemcp.model.VideoResult;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoItem;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoSnippet;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class YoutubeSearchServiceTest {

    private static final int EXPECTED_POOL_SIZE = 25;
    private static final int EXPECTED_TOP_N = 10;

    private YoutubeApiClient apiClient;
    private CategoryResolver categoryResolver;
    private YoutubeSearchService service;

    private static VideoItem video(String id, long views, long likes) {
        return new VideoItem(
                id,
                new VideoSnippet("title-" + id, "channel-" + id, "2024-01-01T00:00:00Z", "desc"),
                new VideoStatistics(String.valueOf(views), String.valueOf(likes), "0"));
    }

    private static List<VideoItem> videos(int count) {
        List<VideoItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(video("v" + i, 1_000L * (i + 1), 10L * (i + 1)));
        }
        return items;
    }

    @BeforeEach
    void setUp() {
        apiClient = mock(YoutubeApiClient.class);
        categoryResolver = mock(CategoryResolver.class);
        service = new YoutubeSearchService(apiClient, categoryResolver);
    }

    // --- searchTopVideosByCategory ---------------------------------------------------

    @Test
    void categorySearchDefaultsToUsWhenRegionIsMissingOrBlank() {
        when(categoryResolver.resolve(anyString(), anyString())).thenReturn("23");
        when(apiClient.listMostPopularByCategory(anyString(), anyString(), anyInt()))
                .thenReturn(videos(3));

        service.searchTopVideosByCategory("Comedy", null);
        service.searchTopVideosByCategory("Comedy", "   ");

        verify(categoryResolver, times(2)).resolve("Comedy", "US");
        verify(apiClient, times(2))
                .listMostPopularByCategory("23", "US", EXPECTED_POOL_SIZE);
    }

    @Test
    void categorySearchUppercasesAndTrimsTheRegionCode() {
        when(categoryResolver.resolve(anyString(), anyString())).thenReturn("20");
        when(apiClient.listMostPopularByCategory(anyString(), anyString(), anyInt()))
                .thenReturn(videos(3));

        service.searchTopVideosByCategory("Gaming", "  fr  ");

        verify(categoryResolver).resolve("Gaming", "FR");
        verify(apiClient).listMostPopularByCategory("20", "FR", EXPECTED_POOL_SIZE);
    }

    @Test
    void nonIsoRegionPlaceholdersAreRejectedBeforeAnyApiCall() {
        // Some MCP clients send the literal placeholder "string" for unset optional params.
        for (String junk : List.of("string", "USA", "U1", "united states")) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.searchTopVideosByCategory("Comedy", junk))
                    .withMessageContaining("ISO 3166-1")
                    .withMessageContaining(junk);
        }

        verifyNoInteractions(apiClient);
        verifyNoInteractions(categoryResolver);
    }

    @Test
    void categorySearchReturnsAtMostTenResultsFromATwentyFiveVideoPool() {
        when(categoryResolver.resolve(anyString(), anyString())).thenReturn("23");
        when(apiClient.listMostPopularByCategory(anyString(), anyString(), anyInt()))
                .thenReturn(videos(EXPECTED_POOL_SIZE));

        List<VideoResult> results = service.searchTopVideosByCategory("Comedy", "US");

        assertThat(results).hasSize(EXPECTED_TOP_N);
        assertThat(results).isSortedAccordingTo(
                Comparator.comparingDouble(VideoResult::weightedRating).reversed());
    }

    @Test
    void categorySearchWithNoCandidatesReturnsAnEmptyList() {
        when(categoryResolver.resolve(anyString(), anyString())).thenReturn("23");
        when(apiClient.listMostPopularByCategory(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());

        assertThat(service.searchTopVideosByCategory("Comedy", "US")).isEmpty();
    }

    @Test
    void categoryResolutionFailureIsPropagatedAndStopsTheSearch() {
        when(categoryResolver.resolve(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Unknown YouTube category 'Nope'"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.searchTopVideosByCategory("Nope", "US"))
                .withMessageContaining("Unknown YouTube category");

        verify(apiClient, never()).listMostPopularByCategory(anyString(), anyString(), anyInt());
    }

    // --- searchTopVideosByTopic ------------------------------------------------------

    @Test
    void blankTopicIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.searchTopVideosByTopic(null, "US"))
                .withMessageContaining("topic");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.searchTopVideosByTopic("  ", "US"));

        verifyNoInteractions(apiClient);
    }

    @Test
    void topicSearchWithoutARegionSearchesGloballyRatherThanDefaultingToUs() {
        when(apiClient.searchVideoIds(anyString(), any(), anyInt())).thenReturn(List.of("a", "b"));
        when(apiClient.listVideosByIds(any())).thenReturn(videos(2));

        service.searchTopVideosByTopic("devops", null);

        verify(apiClient).searchVideoIds(eq("devops"), isNull(), eq(EXPECTED_POOL_SIZE));
    }

    @Test
    void topicSearchNormalizesAProvidedRegionCode() {
        when(apiClient.searchVideoIds(anyString(), any(), anyInt())).thenReturn(List.of("a"));
        when(apiClient.listVideosByIds(any())).thenReturn(videos(1));

        service.searchTopVideosByTopic("devops", "fr");

        verify(apiClient).searchVideoIds("devops", "FR", EXPECTED_POOL_SIZE);
    }

    @Test
    void topicSearchRejectsANonIsoRegionCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.searchTopVideosByTopic("devops", "string"))
                .withMessageContaining("ISO 3166-1");

        verify(apiClient, never()).searchVideoIds(anyString(), any(), anyInt());
    }

    @Test
    void topicSearchPassesTheFoundIdsThroughToTheDetailsLookup() {
        when(apiClient.searchVideoIds(anyString(), any(), anyInt()))
                .thenReturn(List.of("id1", "id2", "id3"));
        when(apiClient.listVideosByIds(any())).thenReturn(videos(3));

        service.searchTopVideosByTopic("kubernetes", "US");

        verify(apiClient).listVideosByIds(List.of("id1", "id2", "id3"));
    }

    @Test
    void topicSearchWithNoMatchesReturnsAnEmptyList() {
        when(apiClient.searchVideoIds(anyString(), any(), anyInt())).thenReturn(List.of());
        when(apiClient.listVideosByIds(any())).thenReturn(List.of());

        assertThat(service.searchTopVideosByTopic("no results for this", "US")).isEmpty();
    }

    @Test
    void topicSearchReturnsAtMostTenResults() {
        when(apiClient.searchVideoIds(anyString(), any(), anyInt())).thenReturn(List.of("a"));
        when(apiClient.listVideosByIds(any())).thenReturn(videos(EXPECTED_POOL_SIZE));

        assertThat(service.searchTopVideosByTopic("devops", "US")).hasSize(EXPECTED_TOP_N);
    }
}
