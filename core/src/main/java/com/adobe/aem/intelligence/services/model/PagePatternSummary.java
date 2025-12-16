package com.adobe.aem.intelligence.services.model;

import java.util.List;
import java.util.Map;

/**
 * Compact summary of page (or subtree) component structure signals.
 */
public class PagePatternSummary {
    private String pagePath;
    private String pageType;
    private List<ComponentInfo> components;
    private List<List<String>> topSequences;
    private Map<String, Long> cooccurrence;
    private List<String> suggestions;

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public List<ComponentInfo> getComponents() {
        return components;
    }

    public void setComponents(List<ComponentInfo> components) {
        this.components = components;
    }

    public List<List<String>> getTopSequences() {
        return topSequences;
    }

    public void setTopSequences(List<List<String>> topSequences) {
        this.topSequences = topSequences;
    }

    public Map<String, Long> getCooccurrence() {
        return cooccurrence;
    }

    public void setCooccurrence(Map<String, Long> cooccurrence) {
        this.cooccurrence = cooccurrence;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}


