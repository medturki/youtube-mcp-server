package com.dev.turkim.youtubemcp.service;

/**
 * MCP tool names and description text, kept out of {@link YoutubeSearchService} so the
 * service reads as logic rather than prose.
 *
 * <p>Everything here is a {@code static final String} with a constant initializer, which is
 * what Java annotations require — text blocks qualify, but enum constants and method calls
 * do not. That constraint is why this is a constants holder rather than an enum.
 *
 * <p>The name constants are the wire contract MCP clients bind to: changing one is a
 * breaking change for anything already calling the tool.
 */
public final class ToolDescriptions {

    private ToolDescriptions() {
    }

    // --- Tool names (the MCP wire contract) ---

    public static final String BY_CATEGORY_NAME = "search_youtube_top_videos_by_category";

    public static final String BY_TOPIC_NAME = "search_youtube_top_videos_by_topic";

    // --- Tool descriptions ---

    public static final String BY_CATEGORY = """
            Search YouTube for the top 10 videos in a given category and return them ranked \
            by a view-weighted rating: videos with more views have their like/view ratio \
            trusted more, while low-view videos are pulled toward the category average so a \
            handful of likes on a barely-watched video can't outrank genuinely popular videos. \
            The category may be a common category name such as 'Music', 'Gaming', 'Comedy', \
            'Sports', 'Education', 'Film & Animation', 'Science & Technology', \
            'News & Politics', 'Howto & Style', 'Entertainment', or a numeric YouTube video \
            category ID. Returns video title, channel, URL, publish date, view count, like \
            count, raw like-to-view ratio, and the final weighted rating.""";

    public static final String BY_TOPIC = """
            Search YouTube for the top 10 videos matching a free-text topic or keyword — \
            e.g. 'devops', 'kubernetes ci/cd', 'sourdough bread' — that isn't necessarily \
            one of YouTube's ~32 fixed video categories. Unlike \
            search_youtube_top_videos_by_category (which browses an official category's \
            trending chart), this performs a real keyword search, so it works for any \
            topic, niche, or technology. Ranked the same view-weighted way: videos with \
            more views have their like/view ratio trusted more, while low-view videos are \
            pulled toward the result set's average so a handful of likes on a \
            barely-watched video can't outrank genuinely popular ones. Returns video \
            title, channel, URL, publish date, view count, like count, raw like-to-view \
            ratio, and the final weighted rating.""";

    // --- Parameter descriptions ---

    public static final String CATEGORY_PARAM =
            "Category name (e.g. 'Gaming') or numeric YouTube category ID (e.g. '20')";

    public static final String TOPIC_PARAM =
            "Free-text search topic or keywords, e.g. 'devops' or 'kubernetes ci/cd'";

    /** Region is used to resolve category names here, so it always has a value. */
    public static final String REGION_PARAM_CATEGORY =
            "Optional ISO 3166-1 alpha-2 region code used to resolve category names, e.g. 'US', 'FR'. Defaults to 'US'.";

    /** Region merely biases keyword search here, so it may be omitted entirely. */
    public static final String REGION_PARAM_TOPIC =
            "Optional ISO 3166-1 alpha-2 region code to bias results, e.g. 'US', 'FR'. Omit for unrestricted global results.";
}