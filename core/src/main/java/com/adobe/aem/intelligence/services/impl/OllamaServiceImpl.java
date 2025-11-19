package com.adobe.aem.intelligence.services.impl;

import com.adobe.aem.intelligence.services.OllamaService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
}

