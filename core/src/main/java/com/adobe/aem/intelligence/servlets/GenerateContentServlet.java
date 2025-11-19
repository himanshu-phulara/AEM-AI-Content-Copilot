package com.adobe.aem.intelligence.servlets;

import com.adobe.aem.intelligence.services.ContentIntelligenceService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
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

    private JsonObject generateHeadlines(SlingHttpServletRequest request) {
        String context = request.getParameter("context");
        int count = getIntParameter(request, "count", 3);
        
        List<String> headlines = intelligenceService.generateHeadlines(context, count);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.add("headlines", GSON.toJsonTree(headlines));
        return result;
    }

    private JsonObject generateBodyCopy(SlingHttpServletRequest request) {
        String topic = request.getParameter("topic");
        String tone = request.getParameter("tone");
        int wordCount = getIntParameter(request, "wordCount", 200);
        
        if (tone == null || tone.isEmpty()) {
            tone = "professional";
        }
        
        String content = intelligenceService.generateBodyCopy(topic, tone, wordCount);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("content", content);
        return result;
    }

    private JsonObject generateMetaDescription(SlingHttpServletRequest request) {
        String pageContent = request.getParameter("content");
        
        String metaDescription = intelligenceService.generateMetaDescription(pageContent);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("metaDescription", metaDescription);
        return result;
    }

    private JsonObject generateAltText(SlingHttpServletRequest request) {
        String imageContext = request.getParameter("context");
        
        String altText = intelligenceService.generateAltText(imageContext);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("altText", altText);
        return result;
    }

    private JsonObject generateVariation(SlingHttpServletRequest request) {
        String originalContent = request.getParameter("content");
        String audience = request.getParameter("audience");
        
        String variation = intelligenceService.generateAudienceVariation(originalContent, audience);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("variation", variation);
        result.addProperty("audience", audience);
        return result;
    }

    private JsonObject analyzeSEO(SlingHttpServletRequest request) {
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        String metaDescription = request.getParameter("metaDescription");
        
        Map<String, Object> analysis = intelligenceService.analyzeSEO(title, content, metaDescription);
        
        JsonObject result = new JsonObject();
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
        
        Map<String, String> suggestion = intelligenceService.suggestNextComponent(components, pageType);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.add("suggestion", GSON.toJsonTree(suggestion));
        return result;
    }

    private JsonObject predictPerformance(SlingHttpServletRequest request) {
        String content = request.getParameter("content");
        String contentType = request.getParameter("contentType");
        
        if (contentType == null || contentType.isEmpty()) {
            contentType = "general";
        }
        
        Map<String, Object> prediction = intelligenceService.predictPerformance(content, contentType);
        
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.add("prediction", GSON.toJsonTree(prediction));
        return result;
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

