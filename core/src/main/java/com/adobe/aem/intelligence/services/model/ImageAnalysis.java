package com.adobe.aem.intelligence.services.model;

import java.util.List;
import java.util.Map;

/**
 * Result of image analysis (caption, tags, recommended alt text, and basic metadata).
 */
public class ImageAnalysis {
    private String source; // damPath or URL
    private String caption;
    private List<String> tags;
    private String suggestedAltText;

    // Optional metadata
    private Integer width;
    private Integer height;
    private String format;
    private List<String> dominantColors;
    private Map<String, String> extra;

    // Raw model output (helpful for debugging)
    private String raw;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getSuggestedAltText() {
        return suggestedAltText;
    }

    public void setSuggestedAltText(String suggestedAltText) {
        this.suggestedAltText = suggestedAltText;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public List<String> getDominantColors() {
        return dominantColors;
    }

    public void setDominantColors(List<String> dominantColors) {
        this.dominantColors = dominantColors;
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = extra;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }
}


