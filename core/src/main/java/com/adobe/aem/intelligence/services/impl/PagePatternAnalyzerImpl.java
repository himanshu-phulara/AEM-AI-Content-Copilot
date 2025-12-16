package com.adobe.aem.intelligence.services.impl;

import com.adobe.aem.intelligence.services.PagePatternAnalyzer;
import com.adobe.aem.intelligence.services.model.ComponentInfo;
import com.adobe.aem.intelligence.services.model.PagePatternSummary;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PagePatternAnalyzer}.
 * <p>
 * This service scans AEM pages (e.g., WKND) using a {@link ResourceResolver} and {@link PageManager} to extract
 * ordered component sequences (by traversing jcr:content containers), aggregates frequent patterns (bigrams/trigrams),
 * and produces a compact {@link PagePatternSummary} along with simple, data-driven suggestions.
 */
@Component(
        service = PagePatternAnalyzer.class,
        immediate = true,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Analyzes AEM page structures and extracts component usage patterns",
                Constants.SERVICE_VENDOR + "=AEM AI Content Copilot"
        }
)
public class PagePatternAnalyzerImpl implements PagePatternAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(PagePatternAnalyzerImpl.class);

    private static final String PN_RESOURCE_TYPE = "sling:resourceType";
    private static final String RES_TYPE_PAGE_CONTENT = "cq:PageContent";
    private static final String DEFAULT_SUBSERVICE = "aem-ai-content-copilot"; // requires service user mapping

    // Known WKND article/adventure sample pages to seed pattern analysis
    private static final List<String> DEFAULT_WKND_PAGES = Arrays.asList(
            "/content/wknd/us/en/magazine/guide-la-skateparks",
            "/content/wknd/us/en/magazine/western-australia",
            "/content/wknd/us/en/adventures/ski-touring-mont-blanc",
            "/content/wknd/us/en/adventures/camping-australia"
    );

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public List<ComponentInfo> extractComponentSequence(final String pagePath) {
        if (StringUtils.isBlank(pagePath)) {
            return Collections.emptyList();
        }
        try (ResourceResolver resolver = getServiceResolver()) {
            if (resolver == null) {
                LOG.warn("Unable to obtain service ResourceResolver; returning empty sequence for {}", pagePath);
                return Collections.emptyList();
            }
            PageManager pm = resolver.adaptTo(PageManager.class);
            if (pm == null) {
                LOG.warn("PageManager unavailable; cannot analyze {}", pagePath);
                return Collections.emptyList();
            }
            Page page = pm.getPage(pagePath);
            if (page == null) {
                LOG.warn("Page not found: {}", pagePath);
                return Collections.emptyList();
            }
            Resource content = page.getContentResource();
            if (content == null) {
                LOG.debug("No jcr:content for page {}", pagePath);
                return Collections.emptyList();
            }
            List<ComponentInfo> out = new ArrayList<>();
            AtomicInteger order = new AtomicInteger(0);
            traverseComponents(content, out, order);
            // Sort by orderIndex just in case
            out.sort(Comparator.comparingInt(ComponentInfo::getOrderIndex));
            return out;
        } catch (Exception e) {
            LOG.error("Failed to extract component sequence for {}: {}", pagePath, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> computeCommonPatterns(final String rootPath, final String pageType, final int maxPages) {
        Map<String, LongAdder> counts = new HashMap<>();

        // Determine candidate pages to analyze
        List<String> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(rootPath)) {
            // Collect pages under rootPath up to maxPages
            candidates.addAll(collectPagesUnder(rootPath, maxPages));
        } else {
            candidates.addAll(DEFAULT_WKND_PAGES);
        }

        int scanned = 0;
        for (String p : candidates) {
            if (scanned >= Math.max(1, maxPages)) break;
            List<ComponentInfo> seq = extractComponentSequence(p);
            if (seq.isEmpty()) {
                continue;
            }
            List<String> tokens = seq.stream()
                    .map(ComponentInfo::getResourceType)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            // bigrams
            addNgrams(tokens, 2, counts);
            // trigrams
            addNgrams(tokens, 3, counts);
            scanned++;
        }

        // Collapse LongAdder -> Long
        Map<String, Long> result = new HashMap<>();
        counts.forEach((k, v) -> result.put(k, v.longValue()));
        return result;
    }

    @Override
    public PagePatternSummary analyzeStructure(final String path, String pageType, final boolean includeChildren) {
        PagePatternSummary summary = new PagePatternSummary();
        summary.setPagePath(path);
        summary.setPageType(resolvePageType(path, pageType));

        // Current page sequence
        List<ComponentInfo> current = extractComponentSequence(path);
        summary.setComponents(current);

        // Build pattern corpus
        Map<String, Long> patterns;
        if (includeChildren) {
            // If analyzing WKND subtree, include known sample pages as seed
            if (StringUtils.startsWith(path, "/content/wknd")) {
                patterns = computeCommonPatterns(path, summary.getPageType(), 50);
                // Also merge in defaults to ensure coverage
                Map<String, Long> seed = computeCommonPatterns(null, summary.getPageType(), DEFAULT_WKND_PAGES.size());
                seed.forEach((k, v) -> patterns.merge(k, v, Long::sum));
            } else {
                patterns = computeCommonPatterns(path, summary.getPageType(), 50);
            }
        } else {
            // Only current page; patterns from current sequence only (n-grams)
            Map<String, LongAdder> counts = new HashMap<>();
            List<String> tokens = current.stream()
                    .map(ComponentInfo::getResourceType)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            addNgrams(tokens, 2, counts);
            addNgrams(tokens, 3, counts);
            patterns = new HashMap<>();
            counts.forEach((k, v) -> patterns.put(k, v.longValue()));
        }

        // Top sequences (pick top N)
        List<Map.Entry<String, Long>> sorted = patterns.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());
        List<List<String>> topSeq = sorted.stream()
                .map(e -> Arrays.asList(e.getKey().split(">")))
                .collect(Collectors.toList());
        summary.setTopSequences(topSeq);
        summary.setCooccurrence(patterns.entrySet().stream()
                .filter(e -> e.getKey().contains(">"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        // Suggestions based on last 1-2 components
        List<String> suggestions = buildSuggestions(current, patterns, summary.getPageType());
        // Add a performance-oriented note derived from pattern coverage
        suggestions.add(buildPerformanceNote(patterns, current));
        summary.setSuggestions(suggestions);

        return summary;
    }

    // ===== Helpers =====

    private void traverseComponents(final Resource root, final List<ComponentInfo> out, final AtomicInteger order) {
        for (Resource child : root.getChildren()) {
            String rt = child.getResourceType();
            if (StringUtils.isNotBlank(rt) && !RES_TYPE_PAGE_CONTENT.equals(rt)) {
                ComponentInfo ci = new ComponentInfo();
                ci.setResourceType(rt);
                ci.setName(child.getName());
                ci.setPath(child.getPath());
                ci.setOrderIndex(order.getAndIncrement());
                // Optional: capture a few lightweight properties (e.g., jcr:title)
                // Skipping heavy extraction for performance
                out.add(ci);
            }
            // Recurse into all children (containers, etc.)
            traverseComponents(child, out, order);
        }
    }

    private List<String> buildSuggestions(final List<ComponentInfo> current,
                                          final Map<String, Long> patterns,
                                          final String pageType) {
        if (current.isEmpty() || patterns.isEmpty()) {
            return Collections.singletonList("No data-driven suggestions available (insufficient pattern data).");
        }
        List<String> suggestions = new ArrayList<>();

        String last = current.get(current.size() - 1).getResourceType();
        // Gather candidates where bigram starts with last
        Map<String, Long> nextCounts = new HashMap<>();
        patterns.forEach((k, v) -> {
            String[] parts = k.split(">");
            if (parts.length == 2 && parts[0].equals(last)) {
                nextCounts.put(parts[1], v);
            }
        });
        if (nextCounts.isEmpty()) {
            suggestions.add("Consider adding a complementary component after '" + simplify(last) +
                    "' to improve flow for " + pageType + " pages (no strong historical bigram found).");
            return suggestions;
        }
        long total = nextCounts.values().stream().mapToLong(Long::longValue).sum();
        List<Map.Entry<String, Long>> ranked = nextCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .collect(Collectors.toList());
        for (Map.Entry<String, Long> e : ranked) {
            String candidate = e.getKey();
            long count = e.getValue();
            double pct = total > 0 ? (count * 100.0 / total) : 0.0;
            String reason = String.format(
                    "Suggest adding '%s' next – appears after '%s' in ~%.1f%% of analyzed %s sequences; " +
                            "this often improves continuity and guides users toward key actions.",
                    simplify(candidate), simplify(last), pct, pageType);
            suggestions.add(reason);
        }
        return suggestions;
    }

    private String buildPerformanceNote(final Map<String, Long> patterns, final List<ComponentInfo> current) {
        long totalPatterns = patterns.values().stream().mapToLong(Long::longValue).sum();
        int uniquePatterns = patterns.size();
        int seqLen = current.size();
        return String.format(
                "Performance note: analyzed %,d pattern occurrences across %d unique sequences; " +
                        "current page has %d components. Aligning with top patterns can accelerate comprehension and increase downstream clicks.",
                totalPatterns, uniquePatterns, seqLen);
    }

    private void addNgrams(final List<String> tokens, final int n, final Map<String, LongAdder> out) {
        if (tokens == null || tokens.size() < n) return;
        for (int i = 0; i <= tokens.size() - n; i++) {
            String key = String.join(">", tokens.subList(i, i + n));
            out.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
    }

    private List<String> collectPagesUnder(final String rootPath, final int maxPages) {
        List<String> pages = new ArrayList<>();
        try (ResourceResolver resolver = getServiceResolver()) {
            if (resolver == null) return pages;
            PageManager pm = resolver.adaptTo(PageManager.class);
            if (pm == null) return pages;
            Page root = pm.getPage(rootPath);
            if (root == null) return pages;
            Deque<Page> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty() && pages.size() < Math.max(1, maxPages)) {
                Page p = stack.pop();
                pages.add(p.getPath());
                Iterator<Page> it = p.listChildren();
                while (it.hasNext() && pages.size() < maxPages) {
                    stack.push(it.next());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to collect pages under {}: {}", rootPath, e.getMessage(), e);
        }
        return pages;
    }

    private String resolvePageType(final String path, final String hinted) {
        if (StringUtils.isNotBlank(hinted)) {
            return hinted.toLowerCase(Locale.ROOT);
        }
        if (StringUtils.contains(path, "/magazine/")) return "article";
        if (StringUtils.contains(path, "/adventures/")) return "adventure";
        if (StringUtils.contains(path, "/products/")) return "product";
        return "default";
    }

    private String simplify(final String resourceType) {
        // Convert a sling:resourceType to a compact label (e.g., core/wcm/components/image/v3/image -> image)
        int idx = resourceType.lastIndexOf('/');
        String last = idx >= 0 ? resourceType.substring(idx + 1) : resourceType;
        // strip version folder like v1, v2, v3
        if (last.matches("v\\d+")) {
            // go one level up
            String parent = resourceType.substring(0, idx);
            int idx2 = parent.lastIndexOf('/');
            if (idx2 >= 0) {
                return parent.substring(idx2 + 1);
            }
        }
        return last;
    }

    private ResourceResolver getServiceResolver() {
        try {
            Map<String, Object> auth = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, DEFAULT_SUBSERVICE);
            return resourceResolverFactory.getServiceResourceResolver(auth);
        } catch (LoginException e) {
            LOG.error("Failed to obtain service ResourceResolver using subservice '{}': {}", DEFAULT_SUBSERVICE, e.getMessage());
            return null;
        }
    }

}


