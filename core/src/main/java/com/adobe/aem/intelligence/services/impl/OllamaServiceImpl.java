package com.adobe.aem.intelligence.services.impl;

import com.adobe.aem.intelligence.services.OllamaService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import com.day.cq.dam.api.Asset;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * Implementation of Ollama AI Service
 * Connects to local Ollama instance for AI content generation
 */
@Component(
    service = OllamaService.class,
    immediate = true
)
@Designate(ocd = OllamaServiceImpl.Config.class)
public class OllamaServiceImpl implements OllamaService {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaServiceImpl.class);
    private static final Gson GSON = new Gson();
    private static final String SUBSERVICE = "aem-ai-content-copilot";

    @ObjectClassDefinition(
        name = "AEM Content Intelligence - Ollama Configuration",
        description = "Configuration for Ollama AI Service"
    )
    public @interface Config {
        @AttributeDefinition(
            name = "Ollama API URL",
            description = "URL of the Ollama API endpoint"
        )
        String ollamaUrl() default "http://localhost:11434";

        @AttributeDefinition(
            name = "Default Model",
            description = "Default Ollama model to use"
        )
        String defaultModel() default "llama3.2:3b";

        @AttributeDefinition(
            name = "Default Temperature",
            description = "Default temperature for generation (0.0 - 1.0)"
        )
        double defaultTemperature() default 0.7;

        @AttributeDefinition(
            name = "Connection Timeout (ms)",
            description = "Timeout for API connections in milliseconds"
        )
        int connectionTimeout() default 30000;
    }

    private String ollamaUrl;
    private String defaultModel;
    private double defaultTemperature;
    private int connectionTimeout;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.ollamaUrl = config.ollamaUrl();
        this.defaultModel = config.defaultModel();
        this.defaultTemperature = config.defaultTemperature();
        this.connectionTimeout = config.connectionTimeout();
        LOG.info("Ollama Service activated. URL: {}, Model: {}", ollamaUrl, defaultModel);
    }

    @Override
    public String generateContent(String prompt) {
        return generateContent(prompt, defaultModel, defaultTemperature);
    }

    @Override
    public String generateContent(String prompt, String model, double temperature) {
        try {
            LOG.debug("Generating content with model: {}, prompt length: {}", model, prompt.length());
            
            // Prepare request payload
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            payload.addProperty("prompt", prompt);
            payload.addProperty("stream", false);
            payload.addProperty("temperature", temperature);

            // Make API call
            String apiUrl = ollamaUrl + "/api/generate";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(connectionTimeout);
            connection.setDoOutput(true);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    JsonObject jsonResponse = GSON.fromJson(response.toString(), JsonObject.class);
                    String generatedText = jsonResponse.get("response").getAsString();
                    
                    LOG.debug("Successfully generated content, length: {}", generatedText.length());
                    return generatedText.trim();
                }
            } else {
                LOG.error("Ollama API returned error code: {}", responseCode);
                return "Error: Unable to generate content. Please check Ollama service.";
            }
        } catch (Exception e) {
            LOG.error("Error calling Ollama API", e);
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            URL url = new URL(ollamaUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            LOG.debug("Ollama service not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String analyzeImage(final String imageBase64, final String prompt) {
        if (StringUtils.isBlank(imageBase64)) {
            return "Error: imageBase64 is empty.";
        }
        final String model = "llava:7b";
        final int timeoutMs = Math.max(60000, this.connectionTimeout); // vision models are slower
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            payload.addProperty("prompt", StringUtils.defaultIfBlank(prompt, "Describe the image."));
            payload.addProperty("stream", false);
            JsonArray images = new JsonArray();
            images.add(imageBase64);
            payload.add("images", images);

            String apiUrl = ollamaUrl + "/api/generate";
            String raw = postJson(apiUrl, payload, timeoutMs, timeoutMs);
            if (raw == null) {
                return "Error: no response from Ollama vision API.";
            }
            try {
                JsonObject json = GSON.fromJson(raw, JsonObject.class);
                if (json != null && json.has("response")) {
                    return json.get("response").getAsString().trim();
                }
            } catch (Exception parse) {
                LOG.debug("Non-JSON or unexpected response format from vision model; returning raw text.");
            }
            return raw.trim();
        } catch (Exception e) {
            LOG.error("Vision analyze error: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    // ===== Helpers =====

    private String postJson(final String urlStr, final JsonObject payload, final int connectTimeoutMs, final int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            try (OutputStream os = new java.io.BufferedOutputStream(connection.getOutputStream())) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }
            int code = connection.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (is == null) {
                LOG.error("Ollama returned HTTP {} with empty body", code);
                return null;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            LOG.error("Error posting to Ollama: {}", e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Helper to read a DAM asset and return its Base64-encoded content.
     * Not exposed on the public interface; can be used by higher-level services.
     */
    public String readDamAssetAsBase64(final String damPath) {
        if (StringUtils.isBlank(damPath)) return null;
        try (ResourceResolver rr = getServiceResolver()) {
            if (rr == null) return null;
            Resource res = rr.getResource(damPath);
            if (res == null) {
                LOG.warn("Asset not found: {}", damPath);
                return null;
            }
            Asset asset = res.adaptTo(Asset.class);
            if (asset == null || asset.getOriginal() == null) {
                LOG.warn("Resource is not a DAM asset or has no original rendition: {}", damPath);
                return null;
            }
            try (InputStream is = asset.getOriginal().getStream()) {
                byte[] bytes = toBytes(is);
                return Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            LOG.error("Failed to read DAM asset {}: {}", damPath, e.getMessage(), e);
            return null;
        }
    }

    private ResourceResolver getServiceResolver() {
        try {
            Map<String, Object> auth = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);
            return resourceResolverFactory.getServiceResourceResolver(auth);
        } catch (LoginException e) {
            LOG.error("Could not get service resolver: {}", e.getMessage());
            return null;
        }
    }

    private byte[] toBytes(final InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }
}

