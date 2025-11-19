package com.adobe.aem.intelligence.servlets;

import com.adobe.aem.intelligence.services.OllamaService;
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

/**
 * Health check servlet to verify AI services status
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/intelligence/health",
        "sling.servlet.methods=[GET]"
    }
)
public class HealthCheckServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckServlet.class);
    private static final Gson GSON = new Gson();

    @Reference
    private OllamaService ollamaService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject result = new JsonObject();
        
        try {
            boolean ollamaAvailable = ollamaService.isAvailable();
            
            result.addProperty("success", true);
            result.addProperty("ollamaAvailable", ollamaAvailable);
            result.addProperty("status", ollamaAvailable ? "healthy" : "degraded");
            result.addProperty("message", ollamaAvailable ? 
                "AI Content Intelligence is ready" : 
                "Ollama service is not available. Please start Ollama.");
            
            if (!ollamaAvailable) {
                result.addProperty("hint", "Run 'ollama serve' to start the service");
            }
            
        } catch (Exception e) {
            LOG.error("Error checking health", e);
            result.addProperty("success", false);
            result.addProperty("error", e.getMessage());
        }
        
        response.getWriter().write(GSON.toJson(result));
    }
}

