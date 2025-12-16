package com.adobe.aem.intelligence.services.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Captures descriptive signals of a "brand voice" and prompt directives/examples
 * to generate content in the learned style.
 */
public class StyleProfile {
    private String id;
    private String rootPath;
    private List<String> toneDescriptors;
    private double averageSentenceLength;
    private String readingLevel; // e.g., Flesch-Kincaid band or qualitative label
    private List<String> keywords;
    private String promptDirectives; // short style instruction block
    private List<String> exampleSentences;
    private Map<String, Object> extra; // extensibility for future attributes
    private Instant created;
    private Instant updated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public List<String> getToneDescriptors() {
        return toneDescriptors;
    }

    public void setToneDescriptors(List<String> toneDescriptors) {
        this.toneDescriptors = toneDescriptors;
    }

    public double getAverageSentenceLength() {
        return averageSentenceLength;
    }

    public void setAverageSentenceLength(double averageSentenceLength) {
        this.averageSentenceLength = averageSentenceLength;
    }

    public String getReadingLevel() {
        return readingLevel;
    }

    public void setReadingLevel(String readingLevel) {
        this.readingLevel = readingLevel;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getPromptDirectives() {
        return promptDirectives;
    }

    public void setPromptDirectives(String promptDirectives) {
        this.promptDirectives = promptDirectives;
    }

    public List<String> getExampleSentences() {
        return exampleSentences;
    }

    public void setExampleSentences(List<String> exampleSentences) {
        this.exampleSentences = exampleSentences;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }
}


