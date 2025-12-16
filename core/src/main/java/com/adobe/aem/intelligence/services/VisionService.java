package com.adobe.aem.intelligence.services;

import com.adobe.aem.intelligence.services.model.ImageAnalysis;

import java.util.Map;

/**
 * Vision AI service for image understanding using a multimodal model (e.g., LLaVA via Ollama).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Analyze DAM assets or external images</li>
 *   <li>Generate captions/tags and suggested alt text</li>
 *   <li>Extract basic image metadata</li>
 * </ul>
 */
public interface VisionService {

    /**
     * Analyzes an AEM DAM asset.
     *
     * @param damPath absolute DAM path (e.g., /content/dam/site/image.jpg)
     * @return {@link ImageAnalysis} with caption/tags/suggestedAltText
     */
    ImageAnalysis analyzeAsset(String damPath);

    /**
     * Analyzes an image located at an external URL.
     *
     * @param imageUrl HTTPS URL to the image
     * @return {@link ImageAnalysis} with caption/tags/suggestedAltText
     */
    ImageAnalysis analyzeUrl(String imageUrl);

    /**
     * Generates an alt text string from a prior {@link ImageAnalysis} result.
     *
     * @param analysis result object from {@link #analyzeAsset(String)} or {@link #analyzeUrl(String)}
     * @return alt text string suitable for accessibility metadata
     */
    String generateAltText(ImageAnalysis analysis);

    /**
     * Extracts basic metadata for a DAM asset (width/height/format, etc).
     *
     * @param damPath absolute DAM path
     * @return map of metadata keys to values (best-effort)
     */
    Map<String, String> extractMetadata(String damPath);
}


