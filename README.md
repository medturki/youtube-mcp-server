# youtube-mcp-server

A [Spring AI](https://docs.spring.io/spring-ai/reference/) **MCP server** that exposes two
YouTube search tools to any MCP client (Claude Desktop, MCP Inspector, a Spring AI MCP
client, and so on).

YouTube's public API no longer exposes dislike counts, so there is no true star rating to
sort by. Both tools therefore fetch a candidate pool and re-rank it by a **view-weighted
rating** built from each video's like/view ratio, rather than returning YouTube's own
view-count ordering unchanged.

## Tools

### `search_youtube_top_videos_by_category(category, regionCode?)`

Top 10 videos in one of YouTube's fixed categories.

- `category` — a category name such as `Gaming`, `Music`, `Comedy`, `Film & Animation`,
  or a numeric YouTube category ID such as `20`. Names are resolved case-insensitively
  against the region's official taxonomy and the result is cached per region.
- `regionCode` — optional ISO 3166-1 alpha-2 code (`US`, `FR`). Defaults to `US`, since a
  region is required to resolve category names.

### `search_youtube_top_videos_by_topic(topic, regionCode?)`

Top 10 videos for a free-text query — `devops`, `kubernetes ci/cd`, `sourdough bread` —
for topics that are not one of YouTube's ~32 fixed categories.

- `topic` — free-text search keywords.
- `regionCode` — optional. Unlike the category tool, omitting it searches **globally**
  rather than defaulting to `US`; here the region only biases results.

Both tools return, per video: `videoId`, `title`, `channelTitle`, `url`, `publishedAt`,
`viewCount`, `likeCount`, `likeToViewRatio` (raw) and `weightedRating` (the sort key).

## How ranking works

**By category** — `videoCategories.list` resolves the name to a numeric ID (cached per
region), then `videos.list?chart=mostPopular` pulls a 25-video candidate pool. That
endpoint is YouTube's purpose-built one for trending videos and returns `snippet` and
`statistics` in a single call, costing 1 quota unit; `search.list` with
`order=viewCount` + `videoCategoryId` costs 100 and is often empty.

**By topic** — `search.list?order=viewCount` returns 25 video IDs, then `videos.list?id=…`
fetches `snippet` and `statistics` for the batch.

Both pools are then re-ranked by `VideoRanker`, which weights each video's like/view ratio
by its view count so that a video with very few views cannot rank first on a handful of
lucky likes. The top 10 are returned.

### Known limitation

The current damping shrinks every score toward the pool's overall like/view ratio. That
average is view-weighted, so high-view videos define it and cannot sit meaningfully above
it — which means a very low-view video with an above-average ratio can still edge ahead by
a tiny margin. This is pinned by
`VideoRankerTest.knownDefect_lowViewFlukeEdgesAheadOfGenuinelyPopularVideo`, whose Javadoc
carries the full analysis. In practice the category tool is largely shielded, because
`chart=mostPopular` only returns videos with substantial view counts; the topic tool is
where it shows.

## Setup

### 1. Get a YouTube Data API v3 key

1. Google Cloud Console → create or select a project.
2. Enable **YouTube Data API v3**.
3. Credentials → Create Credentials → **API key**.
4. Restrict the key to the YouTube Data API v3.

### 2. Provide the key

The key is read from the `YOUTUBE_API_KEY` environment variable and is **never committed**:

```yaml
youtube:
  api:
    key: ${YOUTUBE_API_KEY}
```

```bash
export YOUTUBE_API_KEY=your_key_here      # macOS / Linux
setx YOUTUBE_API_KEY "your_key_here"      # Windows (new shell required)
```

There is no fallback value, so the application fails fast at startup with a clear message
if the variable is unset, rather than booting and returning opaque 400s from YouTube.

### 3. Run

```bash
mvn spring-boot:run
```

Serves the **Streamable HTTP** MCP transport at `http://localhost:8080/mcp` (SSE is also
enabled by the `spring-ai-starter-mcp-server-webmvc` starter).

To run as a **STDIO** server instead — for a desktop MCP client that launches servers as
subprocesses — set `spring.ai.mcp.server.stdio=true` and package a jar:

```bash
mvn clean package
java -jar target/youtube-mcp-server-0.1.0.jar
```

Then point the client at it, e.g. `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "youtube": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/youtube-mcp-server-0.1.0.jar"],
      "env": { "YOUTUBE_API_KEY": "your_key_here" }
    }
  }
}
```

### 4. Try it

```bash
npx @modelcontextprotocol/inspector
```

Point it at `http://localhost:8080/mcp` (streamable-http) and call
`search_youtube_top_videos_by_category` with `category = "Gaming"`.

From a Spring AI MCP client:

```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            youtube:
              url: http://localhost:8080
```

### 5. Expose it to Claude Desktop with a tunnel

Claude's **custom connectors** cannot reach `localhost`. Even though Claude Desktop runs on
your machine, remote connectors are configured and brokered through your Claude account, so
the connection to your MCP server originates from Anthropic's servers rather than from your
local network interface. `http://localhost:8080/mcp` is therefore unreachable, and the URL
you register must be public HTTPS.

If you would rather avoid a tunnel entirely, use the **STDIO** setup in step 3 instead:
Claude Desktop launches the jar as a subprocess and no networking is involved.

Start the server, then open a tunnel to port 8080:

```bash
# ngrok
ngrok http 8080

# or Cloudflare Tunnel (no account needed for a quick tunnel)
cloudflared tunnel --url http://localhost:8080
```

Either prints a public HTTPS URL such as `https://a1b2-c3d4.ngrok-free.app`. Your MCP
endpoint is that URL with `/mcp` appended.

Then in Claude, go to **Customize → Connectors → Add custom connector** and enter:

```
https://<your-tunnel-host>/mcp
```

Claude infers the transport from the URL — a path ending in `/mcp` uses streamable HTTP,
which is what `protocol: STREAMABLE` in `application.yml` serves (a URL ending in `/sse`
would select the older SSE transport instead). Set **Authentication** to **None**, since
this server implements no OAuth flow.

> **Security note.** A tunnel makes your server reachable by anyone who has the URL, and
> with authentication set to None there is nothing to stop them calling it. Every request
> spends your YouTube API quota. Free ngrok and Cloudflare quick-tunnel hostnames are
> random and short-lived, which is obscurity rather than security. Keep the tunnel running
> only while you are actually using it, and stop it (Ctrl-C) when you are done.

## Tests

```bash
mvn test
```

45 tests, no network access required — the YouTube API is stubbed with
`MockRestServiceServer`, and `src/test/resources/application.yml` shadows the main config
with a dummy key so the suite never reads or needs a real one.

| Test class | Covers |
| --- | --- |
| `VideoRankerTest` | ranking maths, damping behaviour, the known defect above |
| `CategoryResolverTest` | name→ID resolution, case-insensitivity, per-region caching |
| `YoutubeSearchServiceTest` | region normalisation and validation, orchestration, top-N |
| `YoutubeApiClientTest` | request paths and query parameters for all four API calls |
| `YoutubeMcpServerApplicationTests` | context loads, both tools exposed, properties bind |

## Project layout

```
src/main/java/com/dev/turkim/youtubemcp/
├── YoutubeMcpServerApplication.java   # entry point
├── client/
│   └── YoutubeApiClient.java          # the only class that knows YouTube API endpoints
├── config/
│   ├── YoutubeProperties.java         # youtube.api.* config binding
│   ├── RestClientConfig.java          # RestClient bean for the YouTube API
│   └── McpToolConfig.java             # registers the service's @Tool methods
├── model/
│   ├── YoutubeApiModels.java          # DTOs for YouTube API JSON responses
│   └── VideoResult.java               # DTO returned by the tools
└── service/
    ├── YoutubeSearchService.java      # the two @Tool methods; orchestration only
    ├── CategoryResolver.java          # category name → ID, cached per region
    ├── VideoRanker.java               # pure ranking logic, no Spring
    └── ToolDescriptions.java          # MCP tool names and description text
```

## Requirements

Java 21, Maven, Spring Boot 4.0.6, Spring AI 2.0.0.
