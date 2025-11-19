package com.adobe.aem.intelligence.services;

/**
 * Service interface for Ollama AI integration
 */
public interface OllamaService {
    
    /**
     * Generate content using Ollama AI
     * @param prompt The prompt to send to the AI
     * @return Generated content
     */
    String generateContent(String prompt);
    
    /**
     * Generate content with specific model and parameters
     * @param prompt The prompt
     * @param model The Ollama model to use (default: llama3.2:3b)
     * @param temperature Temperature for generation (0.0 - 1.0)
     * @return Generated content
     */
    String generateContent(String prompt, String model, double temperature);
    
    /**
     * Check if Ollama service is available
     * @return true if Ollama is running and responsive
     */
    boolean isAvailable();
}

