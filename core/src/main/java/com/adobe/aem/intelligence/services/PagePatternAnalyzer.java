package com.adobe.aem.intelligence.services;

import com.adobe.aem.intelligence.services.model.ComponentInfo;
import com.adobe.aem.intelligence.services.model.PagePatternSummary;

import java.util.List;
import java.util.Map;

/**
 * Service that analyzes AEM page structures and extracts component usage patterns.
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Extract the ordered component sequence of a single page</li>
 *   <li>Compute common sequences/patterns for a subtree (e.g., WKND)</li>
 *   <li>Produce a compact summary with co-occurrence and suggested next components</li>
 * </ul>
 */
public interface PagePatternAnalyzer {

    /**
     * Extracts the ordered component sequence for the page at {@code pagePath}.
     *
     * @param pagePath absolute JCR path to the page (e.g. /content/wknd/us/en)
     * @return ordered list of {@link ComponentInfo} describing components encountered on the page
     */
    List<ComponentInfo> extractComponentSequence(String pagePath);

    /**
     * Computes common patterns (e.g., n-gram sequences) under a content root.
     * The return map keys are compact identifiers for patterns/sequences (such as CSV of resourceTypes),
     * and values are frequency counts.
     *
     * @param rootPath  subtree root to analyze (e.g. /content/wknd/us)
     * @param pageType  optional hint (landing, product, article, default) to bucket analysis
     * @param maxPages  safety cap on total pages scanned
     * @return a map of pattern key to frequency
     */
    Map<String, Long> computeCommonPatterns(String rootPath, String pageType, int maxPages);

    /**
     * High-level structure analysis that returns a summary for the given path, optionally including child pages.
     *
     * @param path             page path (or subtree root)
     * @param pageType         optional hint (landing, product, article, default)
     * @param includeChildren  if true, analyze children under the given path
     * @return a {@link PagePatternSummary} containing key signals and suggestions
     */
    PagePatternSummary analyzeStructure(String path, String pageType, boolean includeChildren);
}


