package com.adobe.aem.intelligence.services;

import java.util.List;
import java.util.Map;

/**
 * Main Content Intelligence Service
 * Provides AI-powered content generation and optimization
 */
public interface ContentIntelligenceService {
    
    /**
     * Generate headline options
     * @param context Context about the page/content
     * @param count Number of options to generate
     * @return List of headline options
     */
    List<String> generateHeadlines(String context, int count);
    
    /**
     * Generate body copy
     * @param topic Topic or subject
     * @param tone Tone (professional, casual, creative)
     * @param wordCount Approximate word count
     * @return Generated body copy
     */
    String generateBodyCopy(String topic, String tone, int wordCount);
    
    /**
     * Generate meta description for SEO
     * @param pageContent Page content summary
     * @return SEO-optimized meta description
     */
    String generateMetaDescription(String pageContent);
    
    /**
     * Generate alt text for image
     * @param imageContext Context about the image
     * @return Alt text
     */
    String generateAltText(String imageContext);
    
    /**
     * Generate content variations for different audiences
     * @param originalContent Original content
     * @param audience Target audience (enterprise, smb, b2c, etc.)
     * @return Adapted content
     */
    String generateAudienceVariation(String originalContent, String audience);
    
    /**
     * Analyze content and provide SEO score
     * @param title Page title
     * @param content Page content
     * @param metaDescription Meta description
     * @return SEO analysis result with score and suggestions
     */
    Map<String, Object> analyzeSEO(String title, String content, String metaDescription);
    
    /**
     * Suggest next best component to add
     * @param currentComponents List of current components on page
     * @param pageType Type of page (product, landing, article, etc.)
     * @return Recommended component with reasoning
     */
    Map<String, String> suggestNextComponent(List<String> currentComponents, String pageType);
    
    /**
     * Predict content performance
     * @param content Content to analyze
     * @param contentType Type of content
     * @return Performance prediction with score
     */
    Map<String, Object> predictPerformance(String content, String contentType);
}

