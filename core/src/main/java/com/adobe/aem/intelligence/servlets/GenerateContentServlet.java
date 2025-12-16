package com.adobe.aem.intelligence.servlets;

import com.adobe.aem.intelligence.services.ContentIntelligenceService;
import com.adobe.aem.intelligence.services.BrandVoiceService;
import com.adobe.aem.intelligence.services.OllamaService;
import com.adobe.aem.intelligence.services.PagePatternAnalyzer;
import com.adobe.aem.intelligence.services.model.ComponentInfo;
import com.adobe.aem.intelligence.services.model.PagePatternSummary;
import com.day.cq.dam.api.Asset;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Servlet for content generation endpoints
 * Exposes AI capabilities as REST APIs
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/intelligence/generate",
        "sling.servlet.methods=[POST,GET]"
    }
)
public class GenerateContentServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateContentServlet.class);
    private static final Gson GSON = new Gson();

    @Reference
    private ContentIntelligenceService intelligenceService;

    @Reference
    private BrandVoiceService brandVoiceService;

    @Reference
    private OllamaService ollamaService;

    @Reference
    private PagePatternAnalyzer pagePatternAnalyzer;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String action = request.getParameter("action");
            if (action == null || action.trim().isEmpty()) {
                LOG.warn("Missing required parameter 'action'");
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Missing required parameter: action");
                response.getWriter().write(GSON.toJson(err));
                return;
            }
            LOG.debug("Generate content request - action: {}", action);
            
            JsonObject result = new JsonObject();
            
            switch (action) {
                case "headlines":
                    result = generateHeadlines(request);
                    break;
                case "bodycopy":
                    result = generateBodyCopy(request);
                    break;
                case "metadescription":
                    result = generateMetaDescription(request);
                    break;
                case "alttext":
                    result = generateAltText(request);
                    break;
                case "variation":
                    result = generateVariation(request);
                    break;
                case "seo":
                    result = analyzeSEO(request);
                    break;
                case "suggest":
                    result = suggestComponent(request);
                    break;
                case "predict":
                    result = predictPerformance(request);
                    break;
                case "trainbrand":
                    result = trainBrandVoice(request);
                    break;
                case "brandvoice":
                    result = generateInBrandVoice(request);
                    break;
                case "analyzeimage":
                    result = analyzeImage(request);
                    break;
                // 'structure' action kept for compatibility; UI no longer uses it
                default:
                    result.addProperty("success", false);
                    result.addProperty("error", "Unknown action: " + action);
            }
            
            response.getWriter().write(GSON.toJson(result));
            
        } catch (Exception e) {
            LOG.error("Error processing content generation request", e);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", e.getMessage());
            response.getWriter().write(GSON.toJson(error));
        }
    }

    private JsonObject analyzeImage(SlingHttpServletRequest request) {
        final String damPath = request.getParameter("damPath");
        final String rawPrompt = request.getParameter("prompt");
        final String prompt = (rawPrompt != null && !rawPrompt.trim().isEmpty())
                ? rawPrompt
                : "Describe this image, then provide concise alt text prefixed with 'ALT:'";
        JsonObject out = new JsonObject();
        if (StringUtils.isBlank(damPath) || !damPath.startsWith("/content/dam/")) {
            LOG.warn("analyzeimage: invalid or missing damPath: {}", damPath);
            out.addProperty("success", false);
            out.addProperty("error", "Please provide a valid DAM path starting with /content/dam/");
            return out;
        }
        try {
            // Read DAM asset bytes and convert to Base64
            Resource res = request.getResourceResolver().getResource(damPath);
            if (res == null) {
                out.addProperty("success", false);
                out.addProperty("error", "Asset not found: " + damPath);
                return out;
            }
            Asset asset = res.adaptTo(Asset.class);
            if (asset == null || asset.getOriginal() == null) {
                out.addProperty("success", false);
                out.addProperty("error", "Resource is not a DAM asset or has no original rendition");
                return out;
            }
            String imageBase64;
            try (InputStream is = asset.getOriginal().getStream()) {
                imageBase64 = Base64.getEncoder().encodeToString(toBytes(is));
            }
            // Call vision-capable model
            String resp = ollamaService.analyzeImage(imageBase64, prompt);
            if (resp == null) {
                out.addProperty("success", false);
                out.addProperty("error", "No response from vision model");
                return out;
            }
            // Split description and ALT if possible (case-insensitive, robust)
            String description = resp;
            String alt = "";
            String[] lines = resp.split("\\r?\\n");
            for (String line : lines) {
                String t = line.trim();
                String low = t.toLowerCase();
                if (low.startsWith("alt:") || low.startsWith("alt text:") || low.startsWith("alt -") || low.equals("alt")) {
                    int colon = t.indexOf(':');
                    if (colon >= 0 && colon + 1 < t.length()) {
                        alt = t.substring(colon + 1).trim();
                    } else {
                        // 'ALT' without colon; take remainder after keyword if present
                        alt = t.replaceFirst("(?i)^alt(\\s*text)?\\s*[-:]?\\s*", "").trim();
                    }
                    break;
                }
            }
            if (alt.isEmpty()) {
                // fallback: try inline "ALT:" inside the paragraph
                String low = resp.toLowerCase();
                int pos = low.indexOf("alt:");
                if (pos >= 0) {
                    alt = resp.substring(pos + 4).trim();
                    description = resp.substring(0, pos).trim();
                }
            } else {
                // If we extracted an explicit ALT line, drop that line from description
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    String low = line.trim().toLowerCase();
                    if (low.startsWith("alt:") || low.startsWith("alt text:") || low.startsWith("alt -") || low.equals("alt")) {
                        continue;
                    }
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                description = sb.toString().trim();
            }
            if (alt.isEmpty()) {
                alt = deriveAltFromDescription(description);
            }
            out.addProperty("success", true);
            out.addProperty("description", description);
            // safety cap for alt text length
            if (alt.length() > 125) alt = alt.substring(0, 125);
            out.addProperty("altText", alt);
            return out;
        } catch (Exception e) {
            LOG.error("Vision analyze error for {}: {}", damPath, e.getMessage(), e);
            out.addProperty("success", false);
            out.addProperty("error", e.getMessage());
            return out;
        }
    }

    private byte[] toBytes(final InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }

    private String deriveAltFromDescription(final String description) {
        if (description == null || description.trim().isEmpty()) {
            return "";
        }
        String text = description.trim();
        // Take first sentence if possible
        String[] parts = text.split("(?<=[.!?])\\s+");
        String first = parts.length > 0 ? parts[0].trim() : text;
        if (first.length() > 125) {
            first = first.substring(0, 125);
        }
        // Remove quotes
        first = first.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
        return first;
    }

    private JsonObject suggestStructure(SlingHttpServletRequest request) {
        JsonObject out = new JsonObject();
        try {
            // Optional inputs
            String pageType = request.getParameter("pageType");
            // If a specific path is provided, honor it; else default to WKND root
            String path = request.getParameter("path");
            if (path == null || path.trim().isEmpty()) {
                // try to infer from Referer if editor, else fallback WKND
                String ref = request.getHeader("Referer");
                if (ref != null && ref.contains("/editor.html/")) {
                    int idx = ref.indexOf("/editor.html/");
                    String rest = ref.substring(idx + "/editor.html".length());
                    int q = rest.indexOf('?');
                    String page = q >= 0 ? rest.substring(0, q) : rest;
                    path = page;
                } else {
                    path = "/content/wknd/us";
                }
            }
            boolean includeChildren = true;

            if (pagePatternAnalyzer == null) {
                out.addProperty("success", false);
                out.addProperty("error", "Pattern analyzer unavailable");
                return out;
            }

            PagePatternSummary summary = pagePatternAnalyzer.analyzeStructure(path, pageType, includeChildren);
            JsonObject jsonSummary = new JsonObject();
            if (summary != null) {
                jsonSummary.addProperty("pagePath", summary.getPagePath());
                jsonSummary.addProperty("pageType", summary.getPageType());

                if (summary.getComponents() != null) {
                    com.google.gson.JsonArray comps = new com.google.gson.JsonArray();
                    for (ComponentInfo ci : summary.getComponents()) {
                        JsonObject c = new JsonObject();
                        c.addProperty("name", ci.getName() != null ? ci.getName() : "");
                        c.addProperty("resourceType", ci.getResourceType() != null ? ci.getResourceType() : "");
                        comps.add(c);
                    }
                    jsonSummary.add("components", comps);
                } else {
                    jsonSummary.add("components", new com.google.gson.JsonArray());
                }

                if (summary.getSuggestions() != null) {
                    com.google.gson.JsonArray sugg = new com.google.gson.JsonArray();
                    for (String s : summary.getSuggestions()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("component", s);
                        obj.addProperty("rationale", "");
                        obj.addProperty("confidence", 0.5);
                        obj.addProperty("source", "pattern");
                        sugg.add(obj);
                    }
                    jsonSummary.add("suggestions", sugg);
                } else {
                    jsonSummary.add("suggestions", new com.google.gson.JsonArray());
                }
            } else {
                out.addProperty("success", false);
                out.addProperty("error", "No structure summary available for path: " + path);
                return out;
            }

            out.addProperty("success", true);
            out.add("summary", jsonSummary);
            return out;
        } catch (Exception e) {
            out.addProperty("success", false);
            out.addProperty("error", e.getMessage());
            return out;
        }
    }

    private JsonObject generateHeadlines(SlingHttpServletRequest request) {
        String context = request.getParameter("context");
        int count = getIntParameter(request, "count", 3);
        
        JsonObject result = new JsonObject();
        if (context == null || context.trim().isEmpty()) {
            LOG.warn("headlines: missing context");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide context/topic for headlines");
            return result;
        }
        if (count <= 0) count = 3;
        
        List<String> headlines = intelligenceService.generateHeadlines(context, count);
        
        result.addProperty("success", true);
        result.add("headlines", GSON.toJsonTree(headlines));
        return result;
    }

    private JsonObject generateBodyCopy(SlingHttpServletRequest request) {
        String topic = request.getParameter("topic");
        String tone = request.getParameter("tone");
        int wordCount = getIntParameter(request, "wordCount", 200);
        
        JsonObject result = new JsonObject();
        if (topic == null || topic.trim().isEmpty()) {
            LOG.warn("bodycopy: missing topic");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide a topic for body copy generation");
            return result;
        }
        if (tone == null || tone.isEmpty()) {
            tone = "professional";
        }
        if (wordCount <= 0) wordCount = 200;
        
        String content = intelligenceService.generateBodyCopy(topic, tone, wordCount);
        
        result.addProperty("success", true);
        result.addProperty("content", content);
        return result;
    }

    private JsonObject generateMetaDescription(SlingHttpServletRequest request) {
        String pageContent = request.getParameter("content");
        
        JsonObject result = new JsonObject();
        if (pageContent == null || pageContent.trim().isEmpty()) {
            LOG.warn("metadescription: missing content");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide content to summarize for meta description");
            return result;
        }
        String metaDescription = intelligenceService.generateMetaDescription(pageContent);
        
        result.addProperty("success", true);
        result.addProperty("metaDescription", metaDescription);
        return result;
    }

    private JsonObject generateAltText(SlingHttpServletRequest request) {
        String imageContext = request.getParameter("context");
        
        JsonObject result = new JsonObject();
        if (imageContext == null || imageContext.trim().isEmpty()) {
            LOG.warn("alttext: missing context");
            result.addProperty("success", false);
            result.addProperty("error", "Please describe the image context to generate alt text");
            return result;
        }
        String altText = intelligenceService.generateAltText(imageContext);
        
        result.addProperty("success", true);
        result.addProperty("altText", altText);
        return result;
    }

    private JsonObject generateVariation(SlingHttpServletRequest request) {
        String originalContent = request.getParameter("content");
        String audience = request.getParameter("audience");
        
        JsonObject result = new JsonObject();
        if (originalContent == null || originalContent.trim().isEmpty()) {
            LOG.warn("variation: missing content");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide content to adapt for audience variations");
            return result;
        }
        if (audience == null || audience.trim().isEmpty()) {
            LOG.warn("variation: missing audience");
            result.addProperty("success", false);
            result.addProperty("error", "Please select a target audience");
            return result;
        }
        
        String variation = intelligenceService.generateAudienceVariation(originalContent, audience);
        
        result.addProperty("success", true);
        result.addProperty("variation", variation);
        result.addProperty("audience", audience);
        return result;
    }

    private JsonObject analyzeSEO(SlingHttpServletRequest request) {
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        String metaDescription = request.getParameter("metaDescription");
        
        JsonObject result = new JsonObject();
        if ((title == null || title.trim().isEmpty()) && (content == null || content.trim().isEmpty())) {
            LOG.warn("seo: missing title and content");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide at least a title or content for SEO analysis");
            return result;
        }
        
        Map<String, Object> analysis = intelligenceService.analyzeSEO(title, content, metaDescription);
        
        result.addProperty("success", true);
        result.add("analysis", GSON.toJsonTree(analysis));
        return result;
    }

    private JsonObject suggestComponent(SlingHttpServletRequest request) {
        String componentsParam = request.getParameter("components");
        String pageType = request.getParameter("pageType");
        
        List<String> components = componentsParam != null ? 
            Arrays.asList(componentsParam.split(",")) : 
            Arrays.asList();
        
        if (pageType == null || pageType.isEmpty()) {
            pageType = "default";
        }
        
        JsonObject result = new JsonObject();
        if (components.isEmpty()) {
            LOG.warn("suggest: empty components list");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide at least one current component");
            return result;
        }
        
        Map<String, String> suggestion = intelligenceService.suggestNextComponent(components, pageType);
        
        result.addProperty("success", true);
        result.add("suggestion", GSON.toJsonTree(suggestion));
        return result;
    }

    private JsonObject predictPerformance(SlingHttpServletRequest request) {
        String content = request.getParameter("content");
        String contentType = request.getParameter("contentType");
        
        JsonObject result = new JsonObject();
        if (content == null || content.trim().isEmpty()) {
            LOG.warn("predict: missing content");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide content to predict performance");
            return result;
        }
        
        if (contentType == null || contentType.isEmpty()) {
            contentType = "general";
        }
        
        Map<String, Object> prediction = intelligenceService.predictPerformance(content, contentType);
        
        result.addProperty("success", true);
        result.add("prediction", GSON.toJsonTree(prediction));
        return result;
    }

    private JsonObject trainBrandVoice(SlingHttpServletRequest request) {
        String root = request.getParameter("root");
        JsonObject result = new JsonObject();
        if (root == null || root.trim().isEmpty()) {
            LOG.warn("trainbrand: missing root");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide a content root path (e.g., /content/wknd/us)");
            return result;
        }
        try {
            String profileId = brandVoiceService.trainProfile(root.trim());
            result.addProperty("success", true);
            result.addProperty("profileId", profileId);
            return result;
        } catch (Exception e) {
            LOG.error("Brand voice training failed for root {}: {}", root, e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Training failed: " + e.getMessage());
            return result;
        }
    }

    private JsonObject generateInBrandVoice(SlingHttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        String profileId = request.getParameter("profileId");
        int maxTokens = getIntParameter(request, "maxTokens", 150);
        String context = request.getParameter("context");

        JsonObject result = new JsonObject();
        if (prompt == null || prompt.trim().isEmpty()) {
            LOG.warn("brandvoice: missing prompt");
            result.addProperty("success", false);
            result.addProperty("error", "Please provide a prompt/topic");
            return result;
        }
        try {
            String brand = null;
            if (profileId != null && !profileId.trim().isEmpty()) {
                // Use context-aware generation if context is provided
                if (context != null && !context.trim().isEmpty()) {
                    brand = brandVoiceService.generateInBrandVoice(prompt, profileId.trim(), maxTokens, context.trim());
                } else {
                    brand = brandVoiceService.generateInBrandVoice(prompt, profileId.trim(), maxTokens);
                }
            } else {
                // If no profile provided, synthesize a quick profile from prompt context (fallback to generic tone)
                String genericPrompt = (context != null && !context.trim().isEmpty())
                        ? (prompt + " | Context: " + context.trim())
                        : prompt;
                brand = intelligenceService.generateBodyCopy(genericPrompt, "professional", Math.max(100, maxTokens));
            }
            // Generic baseline for comparison
            String generic = intelligenceService.generateBodyCopy(prompt, "professional", Math.max(100, maxTokens));

            result.addProperty("success", true);
            result.addProperty("brand", brand != null ? brand : "");
            result.addProperty("generic", generic != null ? generic : "");
            if (profileId != null && !profileId.trim().isEmpty()) {
                result.addProperty("profileId", profileId.trim());
            }
            return result;
        } catch (Exception e) {
            LOG.error("Brand voice generation failed: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Brand voice generation failed: " + e.getMessage());
            return result;
        }
    }

    private int getIntParameter(SlingHttpServletRequest request, String name, int defaultValue) {
        String value = request.getParameter(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid integer parameter {}: {}", name, value);
            }
        }
        return defaultValue;
    }
}

