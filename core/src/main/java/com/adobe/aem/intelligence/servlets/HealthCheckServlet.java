package com.adobe.aem.intelligence.servlets;

import com.adobe.aem.intelligence.services.BrandVoiceService;
import com.adobe.aem.intelligence.services.OllamaService;
import com.adobe.aem.intelligence.services.PagePatternAnalyzer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check servlet to verify AI services and dependencies status
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

    // Test assets
    private static final String[] WKND_PAGES = new String[] {
        "/content/wknd/us/en/magazine/guide-la-skateparks",
        "/content/wknd/us/en/adventures/ski-touring-mont-blanc"
    };

    // 1x1 transparent PNG base64 (very small) for vision probe
    // iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAoMBgJmGQn0AAAAASUVORK5CYII=
    private static final String TINY_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAoMBgJmGQn0AAAAASUVORK5CYII=";

    @Reference
    private OllamaService ollamaService;

    @Reference
    private PagePatternAnalyzer pagePatternAnalyzer;

    @Reference
    private BrandVoiceService brandVoiceService;

    @Reference
    private SlingSettingsService slingSettings;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject out = new JsonObject();
        Map<String, Object> components = new HashMap<>();
        boolean overallOk = true;
        long start = System.currentTimeMillis();

        // 1) Ollama - text
        long t1 = System.currentTimeMillis();
        boolean ollamaAvailable = false;
        String ollamaMsg = "";
        try {
            ollamaAvailable = ollamaService != null && ollamaService.isAvailable();
            ollamaMsg = ollamaAvailable ? "Ollama reachable" : "Ollama not reachable";
        } catch (Exception e) {
            ollamaMsg = "Ollama check failed: " + e.getMessage();
            LOG.warn(ollamaMsg);
        }
        long ollamaMs = System.currentTimeMillis() - t1;
        components.put("ollamaText", mapStatus(ollamaAvailable, ollamaMs, ollamaMsg));
        overallOk &= ollamaAvailable;

        // 2) Ollama - vision
        long t2 = System.currentTimeMillis();
        boolean visionOk = false;
        String visionMsg = "";
        try {
            if (ollamaAvailable) {
                String resp = ollamaService.analyzeImage(TINY_PNG_BASE64, "Describe image briefly");
                visionOk = StringUtils.isNotBlank(resp) && !resp.startsWith("Error:");
                visionMsg = visionOk ? "Vision model responded" : (StringUtils.defaultIfBlank(resp, "Vision did not respond"));
            } else {
                visionMsg = "Skipped (Ollama unavailable)";
            }
        } catch (Exception e) {
            visionMsg = "Vision check failed: " + e.getMessage();
            LOG.warn(visionMsg);
        }
        long visionMs = System.currentTimeMillis() - t2;
        components.put("ollamaVision", mapStatus(visionOk, visionMs, visionMsg));
        overallOk &= visionOk;

        // 3) WKND pages accessible
        long t3 = System.currentTimeMillis();
        boolean wkndOk = true;
        String wkndMsg = "";
        try {
            for (String p : WKND_PAGES) {
                Resource r = request.getResourceResolver().getResource(p);
                if (r == null) {
                    wkndOk = false;
                    wkndMsg = "Missing: " + p;
                    break;
                }
            }
            if (wkndOk && wkndMsg.isEmpty()) wkndMsg = "WKND pages accessible";
        } catch (Exception e) {
            wkndOk = false;
            wkndMsg = "WKND access failed: " + e.getMessage();
            LOG.warn(wkndMsg);
        }
        long wkndMs = System.currentTimeMillis() - t3;
        components.put("wkndAccess", mapStatus(wkndOk, wkndMs, wkndMsg));
        overallOk &= wkndOk;

        // 4) Brand voice trained status (cached profile file exists)
        long t4 = System.currentTimeMillis();
        boolean brandOk = false;
        String brandMsg = "";
        String brandPath = cachedPath("brand_profile.json");
        try {
            File f = new File(brandPath);
            brandOk = f.exists() && f.length() > 0;
            brandMsg = brandOk ? ("Profile cached: " + brandPath) : "No cached profile (run warmup)";
        } catch (Exception e) {
            brandMsg = "Brand status check failed: " + e.getMessage();
            LOG.warn(brandMsg);
        }
        long brandMs = System.currentTimeMillis() - t4;
        Map<String, Object> brandMap = mapStatus(brandOk, brandMs, brandMsg);
        brandMap.put("profilePath", brandPath);
        components.put("brandVoice", brandMap);
        overallOk &= brandOk;

        // 5) Patterns cached
        long t5 = System.currentTimeMillis();
        boolean patternsOk = false;
        String patternsMsg = "";
        String patternsPath = cachedPath("patterns.json");
        try {
            File f = new File(patternsPath);
            patternsOk = f.exists() && f.length() > 0;
            patternsMsg = patternsOk ? ("Patterns cached: " + patternsPath) : "No cached patterns (run warmup)";
        } catch (Exception e) {
            patternsMsg = "Patterns check failed: " + e.getMessage();
            LOG.warn(patternsMsg);
        }
        long patternsMs = System.currentTimeMillis() - t5;
        Map<String, Object> patternsMap = mapStatus(patternsOk, patternsMs, patternsMsg);
        patternsMap.put("path", patternsPath);
        components.put("patterns", patternsMap);
        overallOk &= patternsOk;

        // 6) Services available (OSGi refs present)
        long t6 = System.currentTimeMillis();
        boolean servicesOk = (ollamaService != null) && (pagePatternAnalyzer != null) && (brandVoiceService != null);
        String servicesMsg = servicesOk ? "All service references available" : "One or more OSGi services missing";
        long servicesMs = System.currentTimeMillis() - t6;
        Map<String, Object> servicesMap = mapStatus(servicesOk, servicesMs, servicesMsg);
        servicesMap.put("ollamaService", ollamaService != null);
        servicesMap.put("pagePatternAnalyzer", pagePatternAnalyzer != null);
        servicesMap.put("brandVoiceService", brandVoiceService != null);
        components.put("services", servicesMap);
        overallOk &= servicesOk;

        // Overall
        out.addProperty("success", true);
        out.addProperty("status", overallOk ? "healthy" : "degraded");
        out.addProperty("ollamaAvailable", ollamaAvailable);
        out.add("components", GSON.toJsonTree(components));
        out.addProperty("durationMs", System.currentTimeMillis() - start);

        // Warnings
        StringBuilder warn = new StringBuilder();
        if (!ollamaAvailable) warn.append("Ollama unavailable. ");
        if (ollamaAvailable && !visionOk) warn.append("Vision model not responding. ");
        if (!wkndOk) warn.append("WKND pages not accessible. ");
        if (!brandOk) warn.append("Brand profile not cached (run warmup). ");
        if (!patternsOk) warn.append("Patterns not cached (run warmup). ");
        if (!servicesOk) warn.append("OSGi services missing. ");
        out.addProperty("warning", warn.toString().trim());

        // Helpful hint for UI when Ollama is offline
        if (!ollamaAvailable) {
            out.addProperty("hint", "Ensure Ollama is running and reachable at the configured URL (default http://localhost:11434).");
        }

        response.getWriter().write(GSON.toJson(out));
    }

    private Map<String, Object> mapStatus(boolean ok, long ms, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", ok);
        m.put("ms", ms);
        m.put("message", StringUtils.defaultString(message));
        return m;
    }

    private String cachedPath(String fileName) {
        try {
            String home = slingSettings != null ? slingSettings.getSlingHomePath() : null;
            File base = StringUtils.isNotBlank(home) ? new File(home) : new File(System.getProperty("java.io.tmpdir"));
            return new File(new File(base, "var/aem-ai-content-copilot"), fileName).getAbsolutePath();
        } catch (Exception e) {
            return fileName;
        }
    }
}

