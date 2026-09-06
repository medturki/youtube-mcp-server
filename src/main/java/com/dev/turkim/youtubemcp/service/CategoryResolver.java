package com.dev.turkim.youtubemcp.service;

import com.dev.turkim.youtubemcp.client.YoutubeApiClient;
import com.dev.turkim.youtubemcp.model.YoutubeApiModels.CategoryItem;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Resolves a human-friendly YouTube category name (e.g. "Comedy") to its numeric category
 * ID, caching the taxonomy per region. Numeric input is passed through untouched.
 */
@Component
public class CategoryResolver {

    private final YoutubeApiClient apiClient;

    // "category name, lowercased" -> "category id", per region.
    private final Map<String, Map<String, String>> categoryCacheByRegion = new ConcurrentHashMap<>();

    public CategoryResolver(YoutubeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public String resolve(String category, String region) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (category.chars().allMatch(Character::isDigit)) {
            return category; // already a numeric category ID
        }

        Map<String, String> regionCache = categoryCacheByRegion.computeIfAbsent(region, this::loadCategories);
        String id = regionCache.get(category.toLowerCase(Locale.ROOT));
        if (id == null) {
            throw new IllegalArgumentException(
                    "Unknown YouTube category '" + category + "' for region " + region +
                            ". Known categories: " + String.join(", ", regionCache.keySet()));
        }
        return id;
    }

    private Map<String, String> loadCategories(String region) {
        return apiClient.listCategories(region).stream()
                .filter(item -> item.snippet() != null && item.snippet().title() != null)
                .collect(Collectors.toMap(
                        item -> item.snippet().title().toLowerCase(Locale.ROOT),
                        CategoryItem::id,
                        (a, b) -> a));
    }
}