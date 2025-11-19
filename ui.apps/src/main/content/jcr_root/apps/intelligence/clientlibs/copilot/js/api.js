/**
 * AEM Content Intelligence - API Module
 * Handles all communication with backend AI services
 */
(function(window) {
    'use strict';

    const API = {
        baseUrl: '/bin/intelligence',

        /**
         * Check health status of AI services
         */
        checkHealth: async function() {
            try {
                const response = await fetch(`${this.baseUrl}/health`);
                return await response.json();
            } catch (error) {
                console.error('Health check failed:', error);
                return { success: false, error: error.message };
            }
        },

        /**
         * Generate headlines
         */
        generateHeadlines: async function(context, count = 3) {
            return await this._post('/generate', {
                action: 'headlines',
                context: context,
                count: count
            });
        },

        /**
         * Generate body copy
         */
        generateBodyCopy: async function(topic, tone = 'professional', wordCount = 200) {
            return await this._post('/generate', {
                action: 'bodycopy',
                topic: topic,
                tone: tone,
                wordCount: wordCount
            });
        },

        /**
         * Generate meta description
         */
        generateMetaDescription: async function(content) {
            return await this._post('/generate', {
                action: 'metadescription',
                content: content
            });
        },

        /**
         * Generate alt text for image
         */
        generateAltText: async function(context) {
            return await this._post('/generate', {
                action: 'alttext',
                context: context
            });
        },

        /**
         * Generate audience variation
         */
        generateVariation: async function(content, audience) {
            return await this._post('/generate', {
                action: 'variation',
                content: content,
                audience: audience
            });
        },

        /**
         * Analyze SEO
         */
        analyzeSEO: async function(title, content, metaDescription) {
            return await this._post('/generate', {
                action: 'seo',
                title: title,
                content: content,
                metaDescription: metaDescription
            });
        },

        /**
         * Suggest next component
         */
        suggestComponent: async function(components, pageType) {
            return await this._post('/generate', {
                action: 'suggest',
                components: components.join(','),
                pageType: pageType
            });
        },

        /**
         * Predict performance
         */
        predictPerformance: async function(content, contentType = 'general') {
            return await this._post('/generate', {
                action: 'predict',
                content: content,
                contentType: contentType
            });
        },

        /**
         * Get CSRF token from AEM
         */
        _getCSRFToken: async function() {
            try {
                const response = await fetch('/libs/granite/csrf/token.json');
                const data = await response.json();
                return data.token;
            } catch (error) {
                console.error('Failed to get CSRF token:', error);
                return null;
            }
        },

        /**
         * Internal method to make POST requests
         */
        _post: async function(endpoint, data) {
            try {
                // Get CSRF token
                const csrfToken = await this._getCSRFToken();
                
                const formData = new URLSearchParams();
                for (const key in data) {
                    if (data.hasOwnProperty(key)) {
                        formData.append(key, data[key]);
                    }
                }

                const headers = {
                    'Content-Type': 'application/x-www-form-urlencoded',
                };
                
                // Add CSRF token if available
                if (csrfToken) {
                    headers['CSRF-Token'] = csrfToken;
                }

                // Use GET with query parameters to avoid CSRF issues in editor
                const queryString = formData.toString();
                const response = await fetch(`${this.baseUrl}${endpoint}?${queryString}`, {
                    method: 'GET',
                    headers: headers
                });

                return await response.json();
            } catch (error) {
                console.error('API request failed:', error);
                return {
                    success: false,
                    error: error.message
                };
            }
        }
    };

    // Export to window
    window.ContentIntelligenceAPI = API;

})(window);

