package com.adobe.aem.intelligence.services;

import com.adobe.aem.intelligence.services.model.StyleProfile;

import java.util.List;
import java.util.Optional;

/**
 * Service that learns a "brand voice" from AEM content and generates content in that voice.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Train a style profile by crawling a content root (e.g. /content/wknd)</li>
 *   <li>Analyze provided text samples and produce a derived {@link StyleProfile}</li>
 *   <li>Persist and retrieve style profiles</li>
 *   <li>Generate content in a selected style</li>
 * </ul>
 */
public interface BrandVoiceService {

    /**
     * Starts a training job over the given content root and returns a job identifier.
     * Implementations may execute synchronously or asynchronously.
     *
     * @param contentRoot root path to crawl (e.g., /content/wknd/us)
     * @return a jobId or profileId string
     */
    String trainProfile(String contentRoot);

    /**
     * Analyzes raw content samples and returns an in-memory style profile.
     *
     * @param samples list of text samples to analyze
     * @return derived {@link StyleProfile}
     */
    StyleProfile analyzeSamples(List<String> samples);

    /**
     * Generates content in the brand style profile indicated by {@code profileId}.
     *
     * @param prompt     plain prompt/topic/context
     * @param profileId  identifier of a stored {@link StyleProfile}
     * @param maxTokens  soft cap on generated length
     * @return generated text in brand voice
     */
    String generateInBrandVoice(String prompt, String profileId, int maxTokens);

    /**
     * Generates content in the brand style using an optional grounding context (e.g., page text).
     *
     * @param prompt      topic/brief
     * @param profileId   stored profile identifier
     * @param maxTokens   length cap
     * @param context     optional textual context to ground generation (may be null/blank)
     * @return on-brand generated text
     */
    String generateInBrandVoice(String prompt, String profileId, int maxTokens, String context);

    /**
     * Generates content using a provided {@link StyleProfile} (without requiring a stored profile).
     *
     * @param prompt       plain prompt/topic/context
     * @param profile      {@link StyleProfile} directives and examples
     * @param maxTokens    soft cap on generated length
     * @return generated text in brand voice
     */
    String generateInBrandVoice(String prompt, StyleProfile profile, int maxTokens);

    /**
     * Stores a {@link StyleProfile}.
     *
     * @param profile style profile to persist
     * @return the canonical profileId after persistence
     */
    String storeProfile(StyleProfile profile);

    /**
     * Retrieves a stored {@link StyleProfile} by identifier.
     *
     * @param profileId identifier returned by {@link #storeProfile(StyleProfile)} or {@link #trainProfile(String)}
     * @return Optional profile if found
     */
    Optional<StyleProfile> getProfile(String profileId);
}


