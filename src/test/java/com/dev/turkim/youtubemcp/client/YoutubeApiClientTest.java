package com.dev.turkim.youtubemcp.client;

import com.dev.turkim.youtubemcp.config.YoutubeProperties;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.CategoryItem;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.VideoItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pins down the YouTube Data API v3 requests this client builds — paths, query parameters
 * and the shape of what comes back — without touching the network. A typo in a query
 * parameter here is invisible until a live call fails, so it is worth asserting on.
 */
class YoutubeApiClientTest {

    private static final String BASE_URL = "https://youtube.test/v3";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer server;
    private YoutubeApiClient client;

    /** Decoded query parameters of a request, in the order they appear. */
    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getQuery(); // URI.getQuery() is already percent-decoded
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(pair, "");
            } else {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return params;
    }

    private static RequestMatcher path(String expectedPath) {
        return request -> assertThat(request.getURI().getPath())
                .as("request path")
                .isEqualTo(expectedPath);
    }

    private static RequestMatcher param(String name, String expectedValue) {
        return request -> assertThat(queryParams(request.getURI()))
                .as("query parameter '%s'", name)
                .containsEntry(name, expectedValue);
    }

    /** Asserts a query parameter is absent altogether, not merely empty. */
    private static RequestMatcher noParam(String name) {
        return request -> assertThat(queryParams(request.getURI()))
                .as("query parameter '%s' should not be present", name)
                .doesNotContainKey(name);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeApiClient(builder.build(), new YoutubeProperties(API_KEY, BASE_URL));
    }

    // --- videoCategories.list --------------------------------------------------------

    @Test
    void listCategoriesCallsVideoCategoriesWithRegionAndKey() {
        server.expect(method(HttpMethod.GET))
                .andExpect(path("/v3/videoCategories"))
                .andExpect(param("part", "snippet"))
                .andExpect(param("regionCode", "FR"))
                .andExpect(param("key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoCategoryListResponse",
                          "items": [
                            {"id": "23", "snippet": {"title": "Comedy", "assignable": true}},
                            {"id": "20", "snippet": {"title": "Gaming", "assignable": true}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CategoryItem> categories = client.listCategories("FR");

        assertThat(categories).hasSize(2);
        assertThat(categories.getFirst().id()).isEqualTo("23");
        assertThat(categories.getFirst().snippet().title()).isEqualTo("Comedy");
        server.verify();
    }

    @Test
    void listCategoriesReturnsAnEmptyListWhenTheResponseHasNoItems() {
        server.expect(path("/v3/videoCategories"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.listCategories("US")).isEmpty();
        server.verify();
    }

    // --- videos.list?chart=mostPopular -----------------------------------------------

    @Test
    void listMostPopularByCategoryUsesTheMostPopularChartEndpoint() {
        server.expect(method(HttpMethod.GET))
                .andExpect(path("/v3/videos"))
                .andExpect(param("part", "snippet,statistics"))
                .andExpect(param("chart", "mostPopular"))
                .andExpect(param("videoCategoryId", "23"))
                .andExpect(param("regionCode", "US"))
                .andExpect(param("maxResults", "25"))
                .andExpect(param("key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "abc123",
                              "snippet": {
                                "title": "A video",
                                "channelTitle": "A channel",
                                "publishedAt": "2024-05-01T12:00:00Z",
                                "description": "a description",
                                "thumbnails": {"default": {"url": "http://x/y.jpg"}}
                              },
                              "statistics": {"viewCount": "1000", "likeCount": "50", "commentCount": "7"}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<VideoItem> videos = client.listMostPopularByCategory("23", "US", 25);

        assertThat(videos).hasSize(1);
        VideoItem video = videos.getFirst();
        assertThat(video.id()).isEqualTo("abc123");
        assertThat(video.snippet().channelTitle()).isEqualTo("A channel");
        assertThat(video.statistics().viewCount()).isEqualTo("1000");
        server.verify();
    }

    @Test
    void unknownJsonFieldsDoNotBreakDeserialization() {
        server.expect(param("chart", "mostPopular"))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoListResponse",
                          "etag": "abc",
                          "pageInfo": {"totalResults": 1, "resultsPerPage": 1},
                          "items": [
                            {
                              "kind": "youtube#video",
                              "id": "abc123",
                              "contentDetails": {"duration": "PT5M"},
                              "snippet": {"title": "t", "channelTitle": "c",
                                          "publishedAt": "2024-01-01T00:00:00Z",
                                          "description": "d", "tags": ["a", "b"]},
                              "statistics": {"viewCount": "5", "likeCount": "1",
                                             "commentCount": "0", "favoriteCount": "0"}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listMostPopularByCategory("23", "US", 25)).hasSize(1);
        server.verify();
    }

    @Test
    void listMostPopularByCategoryReturnsAnEmptyListWhenTheResponseHasNoItems() {
        server.expect(param("chart", "mostPopular"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.listMostPopularByCategory("23", "US", 25)).isEmpty();
        server.verify();
    }

    // --- search.list -----------------------------------------------------------------

    @Test
    void searchVideoIdsRequestsAViewCountOrderedVideoSearchAndIncludesTheRegion() {
        server.expect(method(HttpMethod.GET))
                .andExpect(path("/v3/search"))
                .andExpect(param("part", "snippet"))
                .andExpect(param("type", "video"))
                .andExpect(param("q", "devops"))
                .andExpect(param("order", "viewCount"))
                .andExpect(param("maxResults", "25"))
                .andExpect(param("regionCode", "FR"))
                .andExpect(param("key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": {"kind": "youtube#video", "videoId": "v1"}},
                            {"id": {"kind": "youtube#video", "videoId": "v2"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("devops", "FR", 25)).containsExactly("v1", "v2");
        server.verify();
    }

    @Test
    void searchVideoIdsOmitsRegionCodeEntirelyWhenRegionIsNull() {
        server.expect(path("/v3/search"))
                .andExpect(param("q", "devops"))
                .andExpect(noParam("regionCode"))
                .andRespond(withSuccess("""
                        {"items": [{"id": {"videoId": "v1"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("devops", null, 25)).containsExactly("v1");
        server.verify();
    }

    @Test
    void searchVideoIdsSkipsResultsThatCarryNoVideoId() {
        server.expect(path("/v3/search"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": {"kind": "youtube#channel", "channelId": "c1"}},
                            {"id": null},
                            {"id": {"videoId": "v2"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("devops", null, 25)).containsExactly("v2");
        server.verify();
    }

    @Test
    void searchVideoIdsReturnsAnEmptyListWhenTheResponseHasNoItems() {
        server.expect(path("/v3/search"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("nothing", null, 25)).isEmpty();
        server.verify();
    }

    // --- videos.list?id=... ----------------------------------------------------------

    @Test
    void listVideosByIdsJoinsTheIdsIntoASingleCommaSeparatedBatch() {
        server.expect(method(HttpMethod.GET))
                .andExpect(path("/v3/videos"))
                .andExpect(param("part", "snippet,statistics"))
                .andExpect(param("id", "v1,v2,v3"))
                .andExpect(param("key", API_KEY))
                .andExpect(noParam("chart"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": "v1", "snippet": {"title": "one", "channelTitle": "c",
                                                     "publishedAt": "2024-01-01T00:00:00Z",
                                                     "description": "d"},
                             "statistics": {"viewCount": "10", "likeCount": "1", "commentCount": "0"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listVideosByIds(List.of("v1", "v2", "v3")))
                .extracting(VideoItem::id)
                .containsExactly("v1");
        server.verify();
    }

    @Test
    void listVideosByIdsMakesNoHttpCallForAnEmptyIdList() {
        assertThat(client.listVideosByIds(List.of())).isEmpty();

        server.verify(); // no expectations were set, so any request would have failed
    }

    @Test
    void listVideosByIdsToleratesAVideoWithoutSnippetOrStatistics() {
        server.expect(param("id", "v1"))
                .andRespond(withSuccess("""
                        {"items": [{"id": "v1"}]}
                        """, MediaType.APPLICATION_JSON));

        List<VideoItem> videos = client.listVideosByIds(List.of("v1"));

        assertThat(videos).hasSize(1);
        assertThat(videos.getFirst().snippet()).isNull();
        assertThat(videos.getFirst().statistics()).isNull();
        server.verify();
    }
}
