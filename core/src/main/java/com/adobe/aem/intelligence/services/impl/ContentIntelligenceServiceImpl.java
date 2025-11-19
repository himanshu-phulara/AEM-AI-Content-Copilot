package com.adobe.aem.intelligence.services.impl;

import com.adobe.aem.intelligence.services.ContentIntelligenceService;
import com.adobe.aem.intelligence.services.OllamaService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of Content Intelligence Service
 */
@Component(service = ContentIntelligenceService.class, immediate = true)
public class ContentIntelligenceServiceImpl implements ContentIntelligenceService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentIntelligenceServiceImpl.class);

    @Reference
    private OllamaService ollamaService;

    @Override
    public List<String> generateHeadlines(String context, int count) {
        LOG.debug("Generating {} headlines for context: {}", count, context);
        
        String prompt = String.format(
            "Generate %d compelling, professional headlines for the following context. " +
            "Make them concise (under 60 characters), engaging, and SEO-friendly. " +
            "Format: Return only the headlines, one per line, numbered 1., 2., etc.\n\n" +
            "Context: %s\n\nHeadlines:",
            count, context
        );
        
        String response = ollamaService.generateContent(prompt, "llama3.2:3b", 0.8);
        return parseNumberedList(response, count);
    }

    @Override
    public String generateBodyCopy(String topic, String tone, int wordCount) {
        LOG.debug("Generating body copy for topic: {}, tone: {}, words: {}", topic, tone, wordCount);
        
        String toneGuide = getToneGuide(tone);
        String prompt = String.format(
            "Write a %s %d-word content piece about: %s\n\n" +
            "%s\n\n" +
            "Make it engaging, clear, and actionable. Do not include a title.\n\n" +
            "Content:",
            toneGuide, wordCount, topic, getToneInstructions(tone)
        );
        
        return ollamaService.generateContent(prompt, "llama3.2:3b", 0.7);
    }

    @Override
    public String generateMetaDescription(String pageContent) {
        LOG.debug("Generating meta description for content length: {}", pageContent.length());
        
        String prompt = String.format(
            "Create a compelling SEO meta description (150-160 characters) for this content. " +
            "Make it descriptive, include key benefits, and encourage clicks. " +
            "Return only the meta description, nothing else.\n\n" +
            "Content summary: %s\n\n" +
            "Meta description:",
            truncate(pageContent, 500)
        );
        
        String result = ollamaService.generateContent(prompt, "llama3.2:3b", 0.6);
        return truncate(result, 160);
    }

    @Override
    public String generateAltText(String imageContext) {
        LOG.debug("Generating alt text for image context: {}", imageContext);
        
        String prompt = String.format(
            "Write a concise, descriptive alt text (under 125 characters) for an image with this context: %s\n\n" +
            "Make it clear, specific, and helpful for accessibility. Return only the alt text.\n\n" +
            "Alt text:",
            imageContext
        );
        
        String result = ollamaService.generateContent(prompt, "llama3.2:3b", 0.5);
        return truncate(result, 125);
    }

    @Override
    public String generateAudienceVariation(String originalContent, String audience) {
        LOG.debug("Generating variation for audience: {}", audience);
        
        Map<String, String> audienceProfiles = getAudienceProfiles();
        String profile = audienceProfiles.getOrDefault(audience.toLowerCase(), 
            "general audience with professional tone");
        
        String prompt = String.format(
            "Rewrite the following content for this audience: %s\n\n" +
            "Audience profile: %s\n\n" +
            "Original content: %s\n\n" +
            "Rewritten content:",
            audience, profile, originalContent
        );
        
        return ollamaService.generateContent(prompt, "llama3.2:3b", 0.7);
    }

    @Override
    public Map<String, Object> analyzeSEO(String title, String content, String metaDescription) {
        LOG.debug("Analyzing SEO for title: {}", title);
        
        Map<String, Object> result = new HashMap<>();
        List<String> suggestions = new ArrayList<>();
        int score = 100;
        
        // Title analysis
        if (title == null || title.isEmpty()) {
            score -= 20;
            suggestions.add("Add a page title");
        } else if (title.length() > 60) {
            score -= 10;
            suggestions.add("Title is too long (max 60 characters)");
        } else if (title.length() < 30) {
            score -= 5;
            suggestions.add("Title could be more descriptive (30-60 characters recommended)");
        }
        
        // Meta description analysis
        if (metaDescription == null || metaDescription.isEmpty()) {
            score -= 20;
            suggestions.add("Add a meta description");
        } else if (metaDescription.length() > 160) {
            score -= 10;
            suggestions.add("Meta description is too long (max 160 characters)");
        } else if (metaDescription.length() < 120) {
            score -= 5;
            suggestions.add("Meta description could be longer (120-160 characters)");
        }
        
        // Content analysis
        if (content == null || content.isEmpty()) {
            score -= 30;
            suggestions.add("Add content to the page");
        } else {
            int wordCount = content.split("\\s+").length;
            if (wordCount < 300) {
                score -= 10;
                suggestions.add("Content is thin (minimum 300 words recommended)");
            }
            
            // Check for headings
            if (!content.contains("<h1") && !content.contains("<h2")) {
                score -= 10;
                suggestions.add("Add headings (H1, H2) to structure content");
            }
        }
        
        // Ensure score doesn't go negative
        score = Math.max(0, score);
        
        result.put("score", score);
        result.put("grade", getGrade(score));
        result.put("suggestions", suggestions);
        result.put("status", score >= 80 ? "excellent" : score >= 60 ? "good" : "needs-improvement");
        
        return result;
    }

    @Override
    public Map<String, String> suggestNextComponent(List<String> currentComponents, String pageType) {
        LOG.debug("Suggesting next component for page type: {}, current count: {}", 
            pageType, currentComponents.size());
        
        Map<String, String> result = new HashMap<>();
        
        // Define common component sequences for different page types
        Map<String, List<String>> pageTypeSequences = getComponentSequences();
        List<String> idealSequence = pageTypeSequences.getOrDefault(pageType.toLowerCase(), 
            pageTypeSequences.get("default"));
        
        // Find the next component not yet added
        for (String component : idealSequence) {
            if (!currentComponents.contains(component)) {
                result.put("component", component);
                result.put("reason", getComponentReason(component, pageType));
                result.put("confidence", "high");
                return result;
            }
        }
        
        // If all components are added, suggest optional enhancements
        result.put("component", "testimonial");
        result.put("reason", "Social proof increases conversion rates by 34%");
        result.put("confidence", "medium");
        
        return result;
    }

    @Override
    public Map<String, Object> predictPerformance(String content, String contentType) {
        LOG.debug("Predicting performance for content type: {}", contentType);
        
        Map<String, Object> result = new HashMap<>();
        
        // Analyze content characteristics
        int wordCount = content.split("\\s+").length;
        boolean hasCallToAction = content.toLowerCase().contains("learn more") || 
                                  content.toLowerCase().contains("get started") ||
                                  content.toLowerCase().contains("contact") ||
                                  content.toLowerCase().contains("try");
        boolean hasList = content.contains("<li>") || content.contains("•");
        boolean hasNumbers = content.matches(".*\\d+.*");
        
        // Calculate engagement score
        double engagementScore = 50.0; // Base score
        
        if (wordCount >= 300 && wordCount <= 1000) {
            engagementScore += 15;
        }
        if (hasCallToAction) {
            engagementScore += 10;
        }
        if (hasList) {
            engagementScore += 10;
        }
        if (hasNumbers) {
            engagementScore += 5;
        }
        if (content.length() > 500) {
            engagementScore += 10;
        }
        
        engagementScore = Math.min(100, engagementScore);
        
        result.put("engagementScore", Math.round(engagementScore));
        result.put("predictedCTR", String.format("%.1f%%", engagementScore / 20));
        result.put("confidence", engagementScore > 70 ? "high" : "medium");
        result.put("recommendation", getPerformanceRecommendation((int) engagementScore));
        
        return result;
    }

    // Helper methods
    
    private List<String> parseNumberedList(String response, int expected) {
        List<String> result = new ArrayList<>();
        String[] lines = response.split("\n");
        
        Pattern pattern = Pattern.compile("^\\d+\\.\\s*(.+)$");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                result.add(matcher.group(1).trim());
            } else if (!line.matches("^\\d+\\.$") && !result.isEmpty()) {
                // Continuation of previous line
                int lastIndex = result.size() - 1;
                result.set(lastIndex, result.get(lastIndex) + " " + line);
            }
        }
        
        // If parsing failed, return split by lines
        if (result.isEmpty()) {
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && result.size() < expected) {
                    result.add(line);
                }
            }
        }
        
        return result;
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        text = text.trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength).trim();
    }
    
    private String getToneGuide(String tone) {
        switch (tone.toLowerCase()) {
            case "professional": return "professional and authoritative";
            case "casual": return "friendly and conversational";
            case "creative": return "creative and engaging";
            case "technical": return "technical and detailed";
            default: return "clear and informative";
        }
    }
    
    private String getToneInstructions(String tone) {
        switch (tone.toLowerCase()) {
            case "professional":
                return "Use industry terminology, maintain formal tone, focus on expertise and credibility.";
            case "casual":
                return "Use conversational language, include relatable examples, be approachable and friendly.";
            case "creative":
                return "Use vivid language, storytelling elements, and engaging metaphors.";
            case "technical":
                return "Include technical details, specifications, and precise terminology.";
            default:
                return "Use clear, straightforward language that's easy to understand.";
        }
    }
    
    private Map<String, String> getAudienceProfiles() {
        Map<String, String> profiles = new HashMap<>();
        profiles.put("enterprise", "Enterprise decision-makers who value ROI, scalability, security, and compliance. Focus on business outcomes and integration capabilities.");
        profiles.put("smb", "Small-medium business owners who value cost-effectiveness, ease of use, and quick implementation. Focus on practical benefits and affordability.");
        profiles.put("b2c", "Individual consumers who value user experience, simplicity, and immediate benefits. Use conversational tone and emotional appeal.");
        profiles.put("technical", "Technical users (developers, IT) who value specifications, APIs, customization options, and technical details.");
        profiles.put("executive", "C-level executives who value strategic impact, competitive advantage, and bottom-line results. Be concise and focus on high-level benefits.");
        return profiles;
    }
    
    private String getGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
    
    private Map<String, List<String>> getComponentSequences() {
        Map<String, List<String>> sequences = new HashMap<>();
        
        sequences.put("product", Arrays.asList(
            "hero", "features", "benefits", "specifications", "testimonials", "pricing", "cta", "faq"
        ));
        
        sequences.put("landing", Arrays.asList(
            "hero", "benefits", "social-proof", "features", "cta", "testimonials"
        ));
        
        sequences.put("article", Arrays.asList(
            "hero", "intro", "content", "image", "quote", "conclusion", "related"
        ));
        
        sequences.put("default", Arrays.asList(
            "hero", "intro", "content", "image", "cta"
        ));
        
        return sequences;
    }
    
    private String getComponentReason(String component, String pageType) {
        Map<String, String> reasons = new HashMap<>();
        reasons.put("hero", "Strong hero sections increase engagement by 40%");
        reasons.put("features", "Feature lists help users quickly understand value");
        reasons.put("testimonials", "Social proof increases conversions by 34%");
        reasons.put("cta", "Clear calls-to-action drive user action");
        reasons.put("pricing", "Transparent pricing builds trust and accelerates decisions");
        reasons.put("faq", "FAQs reduce support inquiries by 60%");
        reasons.put("benefits", "Benefit-focused content resonates better than features");
        
        return reasons.getOrDefault(component, "This component typically performs well on " + pageType + " pages");
    }
    
    private String getPerformanceRecommendation(int score) {
        if (score >= 80) {
            return "Excellent! This content is predicted to perform well.";
        } else if (score >= 60) {
            return "Good foundation. Consider adding a clear call-to-action and more specific examples.";
        } else {
            return "Needs improvement. Add more substantial content, clear CTAs, and structure with headings.";
        }
    }
}

