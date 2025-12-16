package com.adobe.aem.intelligence.services.impl;

import com.adobe.aem.intelligence.services.BrandVoiceService;
import com.adobe.aem.intelligence.services.OllamaService;
import com.adobe.aem.intelligence.services.model.StyleProfile;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.PageManagerFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link BrandVoiceService}.
 * <p>
 * Trains a brand style profile from AEM content, caches it in memory, and generates content in the learned voice
 * by composing a context-rich prompt for {@link OllamaService}.
 */
@Component(
        service = BrandVoiceService.class,
        immediate = true,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Learns brand voice from AEM content and generates on-brand text",
                Constants.SERVICE_VENDOR + "=AEM AI Content Copilot"
        }
)
public class BrandVoiceServiceImpl implements BrandVoiceService {

    private static final Logger LOG = LoggerFactory.getLogger(BrandVoiceServiceImpl.class);
    private static final Gson GSON = new Gson();

    private static final String SUBSERVICE = "aem-ai-content-copilot";
    private static final String DEFAULT_MODEL = "llama3.2:3b";

    @Reference
    private OllamaService ollamaService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private PageManagerFactory pageManagerFactory;

    /**
     * In-memory cache of trained profiles keyed by profileId (or content root).
     */
    private final Map<String, StyleProfile> profileCache = new ConcurrentHashMap<>();

    @Override
    public String trainProfile(final String contentRoot) {
        LOG.info("Training brand voice profile from root: {}", contentRoot);
        List<String> samples = collectTextSamples(contentRoot, 200); // cap to avoid huge prompts
        if (samples.isEmpty()) {
            LOG.warn("No text samples found under {}", contentRoot);
        }
        StyleProfile profile = analyzeSamples(samples);
        profile.setRootPath(contentRoot);
        String profileId = storeProfile(profile);
        LOG.info("Brand voice profile trained and stored: {}", profileId);
        return profileId;
    }

    @Override
    public StyleProfile analyzeSamples(final List<String> samples) {
        String joined = samples.stream()
                .filter(StringUtils::isNotBlank)
                .map(s -> s.length() > 2000 ? s.substring(0, 2000) : s) // bound prompt size
                .collect(Collectors.joining("\n\n"));

        // Heuristic features
        double avgSentenceLen = estimateAverageSentenceLength(joined);
        double questionRatio = estimateQuestionRatio(joined);
        List<String> commonPhrases = topNGrams(joined, 2, 10);

        // Ask Ollama for tone/formality descriptors (best-effort parsing)
        List<String> toneDescriptors = new ArrayList<>();
        String formality = "neutral";
        try {
            String analysisPrompt = "You are a writing style analyst. Analyze the following brand content and return a concise JSON with keys:\n" +
                    "\"tone\": array of 3-5 descriptors (e.g., \"conversational\", \"authoritative\"), " +
                    "\"formality\": one of [very informal, informal, neutral, formal, very formal]. " +
                    "Only return JSON, no prose.\n\n" +
                    "TEXT:\n" + truncate(joined, 4000);
            String resp = ollamaService.generateContent(analysisPrompt, DEFAULT_MODEL, 0.2);
            JsonObject obj = GSON.fromJson(resp, JsonObject.class);
            if (obj != null) {
                JsonArray tones = obj.has("tone") && obj.get("tone").isJsonArray() ? obj.getAsJsonArray("tone") : null;
                if (tones != null) {
                    for (JsonElement el : tones) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String t = el.getAsString().trim();
                            if (!t.isEmpty()) toneDescriptors.add(t);
                        }
                    }
                }
                if (obj.has("formality") && obj.get("formality").isJsonPrimitive()) {
                    formality = obj.get("formality").getAsString();
                }
            }
        } catch (Exception e) {
            LOG.debug("Tone/formality analysis fallback: {}", e.getMessage());
            // fallback heuristics
            toneDescriptors = heuristicTone(joined);
            formality = heuristicFormality(avgSentenceLen);
        }

        StyleProfile profile = new StyleProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setToneDescriptors(dedupLimited(toneDescriptors, 5));
        profile.setAverageSentenceLength(avgSentenceLen);
        profile.setReadingLevel(estimateReadingLevel(avgSentenceLen));
        profile.setKeywords(commonPhrases);
        profile.setPromptDirectives(buildPromptDirectives(profile, questionRatio));
        profile.setExampleSentences(extractExampleSentences(joined, 3));
        profile.setExtra(Collections.singletonMap("formality", formality));
        profile.setCreated(Instant.now());
        profile.setUpdated(Instant.now());
        return profile;
    }

    @Override
    public String generateInBrandVoice(final String prompt, final String profileId, final int maxTokens) {
        StyleProfile profile = getProfile(profileId).orElse(null);
        if (profile == null) {
            LOG.warn("No profile found for id {}. Falling back to generic generation.", profileId);
            return ollamaService.generateContent(buildGenericPrompt(prompt), DEFAULT_MODEL, 0.7);
        }
        return generateInBrandVoice(prompt, profile, maxTokens);
    }

    @Override
    public String generateInBrandVoice(final String prompt, final StyleProfile profile, final int maxTokens) {
        String brandPrompt = buildBrandPrompt(prompt, profile, maxTokens);
        return ollamaService.generateContent(brandPrompt, DEFAULT_MODEL, 0.7);
    }

    @Override
    public String generateInBrandVoice(final String prompt, final String profileId, final int maxTokens, final String context) {
        StyleProfile profile = getProfile(profileId).orElse(null);
        if (profile == null) {
            LOG.warn("No profile found for id {}. Falling back to generic generation.", profileId);
            String base = StringUtils.isNotBlank(context) ? (prompt + "\n\nContext:\n" + truncate(context, 1200)) : prompt;
            return ollamaService.generateContent(buildGenericPrompt(base), DEFAULT_MODEL, 0.7);
        }
        String extraContext = StringUtils.defaultString(context);
        // Lightweight retrieval from content root to ground the generation if no explicit context provided
        List<String> retrieved = Collections.emptyList();
        try {
            if (StringUtils.isBlank(extraContext) && StringUtils.isNotBlank(profile.getRootPath())) {
                retrieved = findRelevantSnippets(prompt, profile.getRootPath(), 5);
            }
        } catch (Exception e) {
            LOG.debug("Context retrieval skipped: {}", e.getMessage());
        }
        String brandPrompt = buildBrandPromptWithContext(prompt, profile, maxTokens, extraContext, retrieved);
        return ollamaService.generateContent(brandPrompt, DEFAULT_MODEL, 0.7);
    }

    @Override
    public String storeProfile(final StyleProfile profile) {
        String id = StringUtils.isNotBlank(profile.getId()) ? profile.getId() : UUID.randomUUID().toString();
        profile.setId(id);
        profile.setUpdated(Instant.now());
        profileCache.put(id, profile);
        // Also index by rootPath for convenience
        if (StringUtils.isNotBlank(profile.getRootPath())) {
            profileCache.put(profile.getRootPath(), profile);
        }
        return id;
    }

    @Override
    public Optional<StyleProfile> getProfile(final String profileId) {
        if (StringUtils.isBlank(profileId)) {
            return Optional.empty();
        }
        StyleProfile p = profileCache.get(profileId);
        return Optional.ofNullable(p);
    }

    // ===== Extra helper not in interface: side-by-side comparison =====

    /**
     * Generates two versions: generic and brand-voice outputs, returns a simple side-by-side comparison string.
     *
     * @param prompt    topic or brief
     * @param profileId brand voice profile id
     * @param maxTokens length cap
     * @return side-by-side comparison text
     */
    public String compareGenericVsBrand(final String prompt, final String profileId, final int maxTokens) {
        String generic = ollamaService.generateContent(buildGenericPrompt(prompt), DEFAULT_MODEL, 0.7);
        String branded = generateInBrandVoice(prompt, profileId, maxTokens);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Generic ===\n").append(generic.trim()).append("\n\n")
          .append("=== Brand Voice ===\n").append(branded.trim()).append("\n");
        return sb.toString();
    }

    // ===== Internal logic =====

    private List<String> collectTextSamples(final String rootPath, final int maxSamples) {
        if (StringUtils.isBlank(rootPath)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (ResourceResolver rr = getServiceResolver()) {
            if (rr == null) return out;
            PageManager pm = pageManagerFactory.getPageManager(rr);
            if (pm == null) return out;
            Page root = pm.getPage(rootPath);
            if (root == null) return out;
            Deque<Page> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty() && out.size() < maxSamples) {
                Page p = stack.pop();
                extractPageTexts(p, out, 10); // limit per page
                Iterator<Page> it = p.listChildren();
                while (it.hasNext() && out.size() < maxSamples) {
                    stack.push(it.next());
                }
            }
        } catch (Exception e) {
            LOG.warn("Error collecting text samples from {}: {}", rootPath, e.getMessage(), e);
        }
        return out;
    }

    private void extractPageTexts(final Page page, final List<String> out, final int maxPerPage) {
        Resource content = page.getContentResource();
        if (content == null) return;
        Deque<Resource> queue = new ArrayDeque<>();
        queue.add(content);
        int count = 0;
        while (!queue.isEmpty() && count < maxPerPage) {
            Resource r = queue.poll();
            ValueMap vm = r.getValueMap();
            // common textual props
            addIfPresent(vm, "jcr:title", out);
            addIfPresent(vm, "jcr:description", out);
            addIfPresent(vm, "text", out);
            addIfPresent(vm, "richText", out);
            addIfPresent(vm, "jcr:content", out);
            // iterate children
            for (Resource c : r.getChildren()) {
                queue.add(c);
            }
            count++;
        }
    }

    private void addIfPresent(final ValueMap vm, final String key, final List<String> out) {
        String v = vm.get(key, String.class);
        if (StringUtils.isNotBlank(v)) {
            out.add(v);
        }
    }

    private String buildBrandPrompt(final String prompt, final StyleProfile profile, final int maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert copywriter. Write in the following brand voice.\n\n")
          .append("=== STYLE GUIDELINES ===\n");
        if (profile.getToneDescriptors() != null && !profile.getToneDescriptors().isEmpty()) {
            sb.append("- Tone: ").append(String.join(", ", profile.getToneDescriptors())).append("\n");
        }
        if (profile.getExtra() != null && profile.getExtra().get("formality") != null) {
            sb.append("- Formality: ").append(profile.getExtra().get("formality")).append("\n");
        }
        if (profile.getReadingLevel() != null) {
            sb.append("- Reading level: ").append(profile.getReadingLevel()).append("\n");
        }
        if (profile.getKeywords() != null && !profile.getKeywords().isEmpty()) {
            sb.append("- Common phrases to consider: ").append(String.join("; ", limit(profile.getKeywords(), 8))).append("\n");
        }
        if (StringUtils.isNotBlank(profile.getPromptDirectives())) {
            sb.append("- Additional directions: ").append(profile.getPromptDirectives()).append("\n");
        }
        if (profile.getExampleSentences() != null && !profile.getExampleSentences().isEmpty()) {
            sb.append("\n=== EXAMPLE LINES ===\n");
            for (String ex : limit(profile.getExampleSentences(), 3)) {
                sb.append("• ").append(ex).append("\n");
            }
        }
        sb.append("\n=== TASK ===\n");
        sb.append("Write up to ").append(Math.max(50, maxTokens)).append(" tokens.\n");
        sb.append("Topic/Prompt: ").append(prompt).append("\n");
        sb.append("Output only the final copy.\n");
        return sb.toString();
    }

    private String buildBrandPromptWithContext(final String prompt, final StyleProfile profile, final int maxTokens,
                                               final String extraContext, final List<String> retrieved) {
        String base = buildBrandPrompt(prompt, profile, maxTokens);
        StringBuilder sb = new StringBuilder(base);
        String ctx = StringUtils.defaultString(extraContext).trim();
        boolean hasRetrieved = retrieved != null && !retrieved.isEmpty();
        if (StringUtils.isNotBlank(ctx) || hasRetrieved) {
            sb.append("\n=== CONTEXT ===\n");
            if (StringUtils.isNotBlank(ctx)) {
                sb.append(truncate(ctx, 1200)).append("\n");
            }
            if (hasRetrieved) {
                for (String line : limit(retrieved, 8)) {
                    sb.append("• ").append(truncate(line, 240)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private List<String> findRelevantSnippets(final String prompt, final String rootPath, final int maxLines) {
        List<String> samples = collectTextSamples(rootPath, 200);
        if (samples.isEmpty()) return Collections.emptyList();
        // Simple keyword scoring
        String[] tokens = Arrays.stream(prompt.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(t -> t.length() >= 3).toArray(String[]::new);
        if (tokens.length == 0) return Collections.emptyList();
        return samples.stream()
                .flatMap(s -> Arrays.stream(s.split("(?<=[.!?])\\s+")))
                .map(String::trim)
                .filter(t -> t.length() >= 20)
                .map(sent -> new AbstractMap.SimpleEntry<>(sent, score(sent.toLowerCase(Locale.ROOT), tokens)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(maxLines)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

    private int score(final String text, final String[] tokens) {
        int sc = 0;
        for (String t : tokens) {
            if (text.contains(t)) sc++;
        }
        return sc;
    }

    private String buildGenericPrompt(final String prompt) {
        return "Write a clear, concise, professional paragraph on: " + prompt + "\n" +
               "Keep it engaging and informative. Output only the final copy.";
    }

    private List<String> limit(List<String> list, int n) {
        if (list == null) return Collections.emptyList();
        return list.size() <= n ? list : list.subList(0, n);
    }

    private List<String> dedupLimited(final List<String> items, final int max) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : items) {
            if (StringUtils.isBlank(s)) continue;
            String norm = s.trim();
            if (norm.isEmpty()) continue;
            set.add(norm);
            if (set.size() >= max) break;
        }
        return new ArrayList<>(set);
    }

    private String estimateReadingLevel(final double avgSentenceLen) {
        if (avgSentenceLen <= 0) return "unknown";
        if (avgSentenceLen >= 22) return "College";
        if (avgSentenceLen >= 17) return "Upper High School";
        if (avgSentenceLen >= 14) return "High School";
        if (avgSentenceLen >= 11) return "Middle School";
        return "General";
    }

    private List<String> extractExampleSentences(final String text, final int max) {
        if (StringUtils.isBlank(text)) return Collections.emptyList();
        String[] sentences = text.split("(?<=[.!?])\\s+");
        List<String> candidates = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() < 20) continue;
            int wc = t.split("\\s+").length;
            if (wc >= 6 && wc <= 24) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            // fallback: take first few non-empty sentences
            for (String s : sentences) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    candidates.add(t);
                    if (candidates.size() >= max) break;
                }
            }
        }
        return limit(candidates, max);
    }

    private String buildPromptDirectives(final StyleProfile profile, final double questionRatio) {
        StringBuilder sb = new StringBuilder();
        sb.append("Prefer ").append(profile.getToneDescriptors() == null || profile.getToneDescriptors().isEmpty()
                ? "a consistent, brand-appropriate tone"
                : String.join(", ", profile.getToneDescriptors()))
          .append(". ");
        if (profile.getAverageSentenceLength() > 0) {
            sb.append("Target average sentence length around ").append(Math.round(profile.getAverageSentenceLength())).append(" words. ");
        }
        if (questionRatio > 0.15) {
            sb.append("Leverage rhetorical questions where helpful. ");
        } else {
            sb.append("Use direct statements more than questions. ");
        }
        return sb.toString().trim();
    }

    private double estimateAverageSentenceLength(final String text) {
        if (StringUtils.isBlank(text)) return 0.0;
        String[] sentences = text.split("(?<=[.!?])\\s+");
        int sentenceCount = 0;
        int tokenTotal = 0;
        for (String s : sentences) {
            String[] tokens = s.trim().split("\\s+");
            if (tokens.length == 0) continue;
            tokenTotal += tokens.length;
            sentenceCount++;
        }
        return sentenceCount == 0 ? 0.0 : (double) tokenTotal / sentenceCount;
        }

    private double estimateQuestionRatio(final String text) {
        if (StringUtils.isBlank(text)) return 0.0;
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length == 0) return 0.0;
        long questions = Arrays.stream(sentences).filter(s -> s.trim().endsWith("?")).count();
        return (double) questions / sentences.length;
    }

    private List<String> heuristicTone(final String text) {
        List<String> tones = new ArrayList<>();
        if (StringUtils.isBlank(text)) return tones;
        int exclaim = StringUtils.countMatches(text, "!");
        double avgLen = estimateAverageSentenceLength(text);
        if (exclaim > 2) tones.add("energetic");
        if (avgLen > 18) tones.add("formal");
        else tones.add("concise");
        if (text.matches(".*\\bwe\\b.*")) tones.add("inclusive");
        return dedupLimited(tones, 5);
    }

    private String heuristicFormality(double avgSentenceLen) {
        if (avgSentenceLen >= 22) return "formal";
        if (avgSentenceLen <= 12) return "informal";
        return "neutral";
    }

    private List<String> topNGrams(final String text, final int n, final int topK) {
        if (StringUtils.isBlank(text)) return Collections.emptyList();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s']", " ");
        String[] tokens = normalized.trim().split("\\s+");
        if (tokens.length < n) return Collections.emptyList();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i <= tokens.length - n; i++) {
            String gram = String.join(" ", Arrays.copyOfRange(tokens, i, i + n));
            // skip trivial tokens
            if (gram.length() < 3) continue;
            counts.merge(gram, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String truncate(final String s, final int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private ResourceResolver getServiceResolver() {
        try {
            Map<String, Object> auth = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);
            return resourceResolverFactory.getServiceResourceResolver(auth);
        } catch (LoginException e) {
            LOG.error("Failed to obtain service resolver: {}", e.getMessage());
            return null;
        }
    }
}


