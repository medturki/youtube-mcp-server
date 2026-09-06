package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.client.YoutubeApiClient;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.CategoryItem;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.CategorySnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryResolverTest {

    private YoutubeApiClient apiClient;
    private CategoryResolver resolver;

    private static CategoryItem category(String id, String title) {
        return new CategoryItem(id, title == null ? null : new CategorySnippet(title));
    }

    @BeforeEach
    void setUp() {
        apiClient = mock(YoutubeApiClient.class);
        resolver = new CategoryResolver(apiClient);
        when(apiClient.listCategories(anyString())).thenReturn(List.of(
                category("23", "Comedy"),
                category("20", "Gaming"),
                category("10", "Music")));
    }

    @Test
    void numericCategoryIdIsPassedThroughWithoutHittingTheApi() {
        assertThat(resolver.resolve("20", "US")).isEqualTo("20");

        verify(apiClient, never()).listCategories(anyString());
    }

    @Test
    void categoryNameResolvesToItsIdRegardlessOfCase() {
        assertThat(resolver.resolve("Comedy", "US")).isEqualTo("23");
        assertThat(resolver.resolve("comedy", "US")).isEqualTo("23");
        assertThat(resolver.resolve("COMEDY", "US")).isEqualTo("23");
    }

    @Test
    void unknownCategoryFailsWithAMessageListingTheKnownOnes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve("Nonexistent", "US"))
                .withMessageContaining("Nonexistent")
                .withMessageContaining("US")
                .withMessageContaining("comedy");
    }

    @Test
    void blankOrNullCategoryIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(null, "US"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve("", "US"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve("   ", "US"));
    }

    @Test
    void taxonomyIsFetchedOncePerRegionAndThenServedFromCache() {
        resolver.resolve("Comedy", "US");
        resolver.resolve("Gaming", "US");
        resolver.resolve("Music", "US");

        verify(apiClient, times(1)).listCategories("US");
    }

    @Test
    void eachRegionGetsItsOwnCacheEntry() {
        resolver.resolve("Comedy", "US");
        resolver.resolve("Comedy", "FR");
        resolver.resolve("Comedy", "US");

        verify(apiClient, times(1)).listCategories("US");
        verify(apiClient, times(1)).listCategories("FR");
    }

    @Test
    void categoriesWithoutASnippetOrTitleAreSkippedRatherThanFailing() {
        when(apiClient.listCategories("US")).thenReturn(List.of(
                category("1", null),                              // no snippet at all
                new CategoryItem("2", new CategorySnippet(null)),  // snippet with no title
                category("23", "Comedy")));

        assertThat(resolver.resolve("Comedy", "US")).isEqualTo("23");
    }

    @Test
    void duplicateTitlesKeepTheFirstIdRatherThanThrowing() {
        when(apiClient.listCategories("US")).thenReturn(List.of(
                category("23", "Comedy"),
                category("99", "comedy")));

        assertThat(resolver.resolve("Comedy", "US")).isEqualTo("23");
    }
}
