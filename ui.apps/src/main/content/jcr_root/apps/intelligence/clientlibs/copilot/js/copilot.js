/**
 * AEM Content Intelligence - AI Co-Pilot UI Controller
 * Main interface for AI-powered content generation
 */
(function($, document) {
    'use strict';

    // Wait for AEM editor to be ready
    if (window.Granite && window.Granite.author) {
        $(document).on('cq-layer-activated', function(event) {
            if (event.layer === 'Edit') {
                initCopilot();
            }
        });
    }

    // Also initialize immediately if in edit mode
    $(document).ready(function() {
        setTimeout(initCopilot, 1000);
    });

    // ============= COMPONENT SELECTOR & CONTENT INSERTION =============
    
    function showComponentSelector(content, contentType) {
        const components = detectEditableComponents();
        
        // Store components globally for later reference
        window.detectedComponents = components;
        
        if (components.length === 0) {
            showNotification('No editable components found. Make sure you are in Edit mode and the page has components.', 'error');
            console.warn('Component detection returned 0 results. Check console logs above for details.');
            return;
        }

        // Store content for later use
        $('#component-selector-modal').data('content', content);
        $('#component-selector-modal').data('contentType', contentType);

        // Populate component list
        const componentList = $('#component-list');
        componentList.empty();

        components.forEach(function(comp, index) {
            const componentHtml = `
                <div class="component-option" data-path="${comp.path}" data-type="${comp.type}">
                    <div class="component-icon">${getComponentIcon(comp.type)}</div>
                    <div class="component-info">
                        <div class="component-name">${escapeHtml(comp.name)}</div>
                        <div class="component-type">${escapeHtml(comp.type)}</div>
                    </div>
                    <div class="component-action">→</div>
                </div>
            `;
            componentList.append(componentHtml);
        });

        // Show modal
        $('#component-selector-modal').fadeIn(200);
    }

    function detectEditableComponents() {
        const components = [];
        
        console.log('Detecting editable components...');
        
        // Multiple strategies to find components
        const selectors = [
            '.cq-Editable',
            '[data-path]',
            '.aem-Grid > *',
            '.cq-Overlay',
            '[data-cq-data-path]',
            '.paragraph',
            '.component'
        ];
        
        // Try to find in main document
        let foundElements = $();
        selectors.forEach(function(selector) {
            const elements = $(selector);
            if (elements.length > 0) {
                console.log('Found', elements.length, 'elements with selector:', selector);
                foundElements = foundElements.add(elements);
            }
        });
        
        // Also check in ContentFrame if it exists (Touch UI editor)
        const iframeSelectors = ['iframe#ContentFrame', 'iframe[name="ContentFrame"]', 'iframe.editor-Frame'];
        
        iframeSelectors.forEach(function(iframeSelector) {
            const contentFrame = document.querySelector(iframeSelector);
            if (contentFrame && contentFrame.contentDocument) {
                console.log('Found iframe with selector:', iframeSelector);
                console.log('Checking iframe for components...');
                
                selectors.forEach(function(selector) {
                    try {
                        const iframeElements = $(contentFrame.contentDocument).find(selector);
                        if (iframeElements.length > 0) {
                            console.log('Found', iframeElements.length, 'elements in iframe with selector:', selector);
                            foundElements = foundElements.add(iframeElements);
                        }
                    } catch(e) {
                        console.warn('Could not access iframe content:', e);
                    }
                });
            }
        });
        
        // Check if we're in Touch UI editor and try to find the content frame
        if (window.location.pathname.includes('/editor.html')) {
            console.log('Detected Touch UI editor mode');
            
            // Try to access Granite editor API
            if (window.Granite && window.Granite.author && window.Granite.author.ContentFrame) {
                console.log('Found Granite ContentFrame');
                const graniteFrame = window.Granite.author.ContentFrame.contentDocument || 
                                   window.Granite.author.ContentFrame.document;
                if (graniteFrame) {
                    selectors.forEach(function(selector) {
                        try {
                            const elements = $(graniteFrame).find(selector);
                            if (elements.length > 0) {
                                console.log('Found', elements.length, 'elements via Granite API with selector:', selector);
                                foundElements = foundElements.add(elements);
                            }
                        } catch(e) {
                            console.warn('Could not access Granite frame:', e);
                        }
                    });
                }
            }
        }
        
        // Remove duplicates and process - filter for text components only
        const processed = new Set();
        foundElements.each(function(index) {
            const $element = $(this);
            
            // Skip if already processed
            const elementId = $element.attr('id') || $element.attr('data-path') || index;
            if (processed.has(elementId)) {
                return;
            }
            
            // Filter: Only include components that can hold text content
            if (!isTextComponent($element)) {
                return;
            }
            
            processed.add(elementId);
            
            const path = $element.data('path') || 
                        $element.attr('data-path') || 
                        $element.attr('data-cq-data-path') ||
                        'component-' + index;
            
            const type = $element.data('component-type') || 
                        $element.attr('data-emptytext') || 
                        $element.attr('data-component-type') ||
                        getComponentTypeFromPath(path) ||
                        $element.prop('tagName') || 
                        'Component';
            
            // Get a readable name
            let name = getReadableComponentName($element, type, path);
            
            components.push({
                path: path,
                type: type,
                name: name,
                element: this
            });
        });
        
        console.log('Total components detected:', components.length);
        console.log('Components:', components);

        return components;
    }

    function isTextComponent($element) {
        // Check if element can hold text content
        const tagName = $element.prop('tagName');
        const classNames = $element.attr('class') || '';
        const dataPath = $element.attr('data-path') || '';
        const emptyText = $element.attr('data-emptytext') || '';
        const dataCmpIs = $element.attr('data-cmp-is') || '';
        
        // Skip non-text Core Components explicitly
        if (dataCmpIs && (dataCmpIs === 'image' || dataCmpIs === 'carousel' || dataCmpIs === 'container')) {
            return false;
        }
        
        // Skip containers and layout components
        const containerClasses = ['container', 'responsivegrid', 'aem-Grid', 'root', 'experiencefragment', 'xfpage', 
                                 'cmp-container', 'cmp-carousel', 'cmp-tabs', 'cmp-accordion'];
        for (let containerClass of containerClasses) {
            if (classNames.includes(containerClass)) {
                return false;
            }
        }
        
        // Skip images explicitly
        if (classNames.includes('cmp-image') || classNames.includes('image') && !classNames.includes('text')) {
            return false;
        }
        
        // Skip if it's a large container (likely layout)
        if ($element.children().length > 10) {
            return false;
        }
        
        // PRIORITY: Include WKND Core Components for text
        const coreComponentTextClasses = ['cmp-title', 'cmp-text', 'cmp-teaser'];
        for (let cmpClass of coreComponentTextClasses) {
            if (classNames.includes(cmpClass)) {
                console.log('Found Core Component:', cmpClass);
                return true;
            }
        }
        
        // Include if it has specific text component indicators in path
        const textIndicators = [
            '/text', '/title', '/heading', '/teaser'
        ];
        
        for (let indicator of textIndicators) {
            if (dataPath.toLowerCase().includes(indicator)) {
                console.log('Found text component by path:', indicator);
                return true;
            }
        }
        
        // Include if emptytext suggests it's for text entry
        if (emptyText && (emptyText.toLowerCase().includes('text') || 
                         emptyText.toLowerCase().includes('title'))) {
            return true;
        }
        
        // Include if it has contenteditable
        if ($element.attr('contenteditable') === 'true') {
            return true;
        }
        
        // Include if it has RichText editor inside
        if ($element.find('.cq-RichText-editable, [contenteditable="true"]').length > 0) {
            return true;
        }
        
        return false;
    }
    
    function getComponentTypeFromPath(path) {
        if (!path || path.indexOf('/') === -1) return null;
        
        const pathLower = path.toLowerCase();
        if (pathLower.includes('/text')) return 'Text Component';
        if (pathLower.includes('/title')) return 'Title Component';
        if (pathLower.includes('/teaser')) return 'Teaser Component';
        if (pathLower.includes('/heading')) return 'Heading Component';
        if (pathLower.includes('/paragraph')) return 'Paragraph Component';
        
        return null;
    }
    
    function getReadableComponentName($element, type, path) {
        // Try to get a meaningful name from the component
        
        // Strategy 1: Check for title or heading in data attributes
        const dataTitle = $element.attr('data-title') || $element.attr('data-cq-title');
        if (dataTitle) {
            return dataTitle;
        }
        
        // Strategy 2: Extract from path
        if (path && path.indexOf('/') > -1) {
            const pathParts = path.split('/');
            let name = pathParts[pathParts.length - 1];
            
            // If it's just a number or generic, try second to last
            if (/^\d+$/.test(name) && pathParts.length > 1) {
                name = pathParts[pathParts.length - 2];
            }
            
            // Clean up the name
            name = name.replace(/_/g, ' ').replace(/-/g, ' ');
            
            if (name && name.length > 2) {
                return name.charAt(0).toUpperCase() + name.slice(1);
            }
        }
        
        // Strategy 3: Use existing text content (limited)
        const text = $element.text().trim();
        if (text.length > 0 && text.length < 50) {
            return text;
        } else if (text.length >= 50) {
            return text.substring(0, 47) + '...';
        }
        
        // Strategy 4: Use component type
        if (type && type !== 'DIV' && type !== 'Component') {
            return type;
        }
        
        // Default
        return 'Text Component';
    }

    function getComponentIcon(type) {
        const typeStr = type.toLowerCase();
        
        if (typeStr.includes('text') || typeStr.includes('paragraph')) return '📝';
        if (typeStr.includes('title') || typeStr.includes('heading')) return '📰';
        if (typeStr.includes('image')) return '🖼️';
        if (typeStr.includes('button') || typeStr.includes('cta')) return '🔘';
        if (typeStr.includes('hero') || typeStr.includes('banner')) return '🎯';
        if (typeStr.includes('list')) return '📋';
        if (typeStr.includes('card') || typeStr.includes('teaser')) return '🃏';
        
        return '📝';
    }

    function applyContentToComponent(content, contentType, componentPath, componentType) {
        console.log('Applying content to component:', {
            content: content,
            contentType: contentType,
            componentPath: componentPath,
            componentType: componentType
        });

        // CRITICAL: In Touch UI editor, ALWAYS look in ContentFrame, NEVER use the overlay!
        let $component = $();
        let $actualContent = $();
        
        console.log('🔍 Starting to look for component in ContentFrame...');
        
        // Step 1: ALWAYS find the actual component in the ContentFrame iframe
        const contentFrame = document.querySelector('iframe#ContentFrame') || 
                           document.querySelector('iframe[name="ContentFrame"]');
        
        if (!contentFrame) {
            console.error('ContentFrame iframe not found!');
            copyToClipboard(content);
            showNotification('Editor iframe not found. Content copied to clipboard.', 'error');
            return;
        }
        
        if (!contentFrame.contentDocument) {
            console.error('Cannot access ContentFrame document!');
            copyToClipboard(content);
            showNotification('Cannot access editor content. Content copied to clipboard.', 'error');
            return;
        }
        
        console.log('✅ Found ContentFrame, now searching for component...');
        
        if (contentFrame && contentFrame.contentDocument) {
            console.log('Looking in ContentFrame for component with path:', componentPath);
            
            // Try to find by data-cmp-path (Core Components use this)
            $actualContent = $(contentFrame.contentDocument).find(`[data-cmp-path="${componentPath}"]`).first();
            
            // Also try with data-path attribute
            if ($actualContent.length === 0) {
                $actualContent = $(contentFrame.contentDocument).find(`[data-path="${componentPath}"]`).first();
                if ($actualContent.length > 0) {
                    console.log('Found component by data-path attribute');
                }
            }
            
            // Try to match by examining all components of this type and finding the right one
            if ($actualContent.length === 0) {
                const pathParts = componentPath.split('/');
                const componentName = pathParts[pathParts.length - 1];
                
                console.log('Looking for component by name and matching path:', componentName);
                
                if (componentName === 'text' || componentPath.includes('/text')) {
                    // Find ALL text components and try to match
                    $(contentFrame.contentDocument).find('.cmp-text, .text').each(function() {
                        const $this = $(this);
                        const thisPath = $this.attr('data-cmp-path') || $this.attr('data-path') || $this.closest('[data-path]').attr('data-path');
                        console.log('Checking text component with path:', thisPath);
                        if (thisPath && thisPath === componentPath) {
                            $actualContent = $this;
                            console.log('MATCHED text component by path!');
                            return false; // break
                        }
                    });
                    
                    // If still not found, just take the first one (fallback)
                    if ($actualContent.length === 0) {
                        $actualContent = $(contentFrame.contentDocument).find('.cmp-text').first();
                        console.log('Fallback: Using first text component found');
                    }
                } else if (componentName === 'title' || componentPath.includes('/title')) {
                    // Find ALL title components and try to match
                    $(contentFrame.contentDocument).find('.cmp-title, .title').each(function() {
                        const $this = $(this);
                        const thisPath = $this.attr('data-cmp-path') || $this.attr('data-path') || $this.closest('[data-path]').attr('data-path');
                        console.log('Checking title component with path:', thisPath);
                        if (thisPath && thisPath === componentPath) {
                            $actualContent = $this;
                            console.log('MATCHED title component by path!');
                            return false; // break
                        }
                    });
                    
                    // If still not found, just take the first one (fallback)
                    if ($actualContent.length === 0) {
                        $actualContent = $(contentFrame.contentDocument).find('.cmp-title').first();
                        console.log('Fallback: Using first title component found');
                    }
                } else if (componentPath.includes('/teaser')) {
                    $actualContent = $(contentFrame.contentDocument).find('.cmp-teaser').first();
                }
            }
        }
        
        if ($actualContent.length === 0) {
            console.error('❌ Could not find actual component content in ContentFrame!');
            console.error('Searched for path:', componentPath);
            copyToClipboard(content);
            showNotification('Could not find component. Content copied to clipboard - please paste manually.', 'info');
            return;
        }

        $component = $actualContent;
        console.log('✅ Found ACTUAL component element in ContentFrame:', $component[0]);
        console.log('Component tag:', $component.prop('tagName'));
        console.log('Component classes:', $component.attr('class'));
        
        // CRITICAL FIX: If we found a <cq> placeholder tag, find the actual rendered component
        if ($component.prop('tagName') === 'CQ') {
            console.log('⚠️  Found CQ placeholder, looking for actual rendered component...');
            
            // The rendered component is usually the next sibling
            let $rendered = $component.next();
            
            // Or could be previous sibling
            if ($rendered.length === 0 || !$rendered.hasClass('cmp-title') && !$rendered.hasClass('cmp-text')) {
                $rendered = $component.prev();
            }
            
            // Or parent might contain both
            if ($rendered.length === 0) {
                $rendered = $component.parent().find('.cmp-title, .cmp-text, .cmp-teaser').first();
            }
            
            if ($rendered.length > 0) {
                console.log('✅ Found rendered component:', $rendered[0]);
                $component = $rendered;
            } else {
                console.error('❌ Could not find rendered component near CQ placeholder');
            }
        }

        // Try multiple strategies to find where to insert content
        let $textArea = $();
        
        console.log('Component HTML:', $component.html ? $component.html().substring(0, 300) : 'N/A');
        console.log('Component classes:', $component.attr('class'));
        
        // Strategy 1: WKND Title Component - Find the actual heading element inside
        if (componentPath.includes('/title') || $component.hasClass('cmp-title')) {
            console.log('Detected Title component, searching for heading...');
            
            // Look for the heading inside .cmp-title__text first
            let $titleText = $component.find('.cmp-title__text').first();
            if ($titleText.length > 0) {
                console.log('Found .cmp-title__text wrapper');
                // Now find the actual heading inside it
                $textArea = $titleText.find('h1, h2, h3, h4, h5, h6').first();
                if ($textArea.length === 0) {
                    // The wrapper itself might be the heading
                    if ($titleText.is('h1, h2, h3, h4, h5, h6')) {
                        $textArea = $titleText;
                    }
                }
            }
            
            // Fallback to finding any heading in the component
            if ($textArea.length === 0) {
                $textArea = $component.find('h1, h2, h3, h4, h5, h6').first();
            }
            
            if ($textArea.length > 0) {
                console.log('Found Title component heading element:', $textArea.prop('tagName'));
                console.log('Heading element classes:', $textArea.attr('class'));
                console.log('Current heading text:', $textArea.text());
            } else {
                console.warn('Could not find heading element in title component!');
            }
        }
        
        // Strategy 2: WKND Text Component - Find the paragraph/div inside
        if ($textArea.length === 0 && (componentPath.includes('/text') || $component.hasClass('cmp-text'))) {
            console.log('Detected Text component, searching for paragraph...');
            
            $textArea = $component.find('.cmp-text__paragraph').first();
            if ($textArea.length === 0) {
                $textArea = $component.find('p').first();
            }
            if ($textArea.length === 0) {
                $textArea = $component.find('div.text').first();
            }
            
            if ($textArea.length > 0) {
                console.log('Found Text component paragraph element:', $textArea.prop('tagName'));
                console.log('Paragraph element classes:', $textArea.attr('class'));
                console.log('Current paragraph text:', $textArea.text());
            } else {
                console.warn('Could not find paragraph element in text component!');
            }
        }
        
        // Strategy 3: Teaser Component - Find title or description
        if ($textArea.length === 0 && (componentPath.includes('/teaser') || $component.hasClass('cmp-teaser'))) {
            $textArea = $component.find('.cmp-teaser__title, .cmp-teaser__description').first();
            if ($textArea.length > 0) {
                console.log('Found Teaser component text element');
            }
        }
        
        // Strategy 4: Look for ANY heading or paragraph as direct child
        if ($textArea.length === 0) {
            // First try direct children
            $textArea = $component.children('h1, h2, h3, h4, h5, h6, p').first();
            if ($textArea.length === 0) {
                // Then try descendants
                $textArea = $component.find('h1, h2, h3, h4, h5, h6, p').first();
            }
            if ($textArea.length > 0) {
                console.log('Found heading/paragraph element:', $textArea.prop('tagName'));
            }
        }
        
        // Strategy 5: Look for contenteditable (when component is being edited)
        if ($textArea.length === 0) {
            $textArea = $component.find('[contenteditable="true"]').first();
            if ($textArea.length > 0) {
                console.log('Found contenteditable area');
            }
        }
        
        // Strategy 6: Last resort - look for any element with text content
        if ($textArea.length === 0) {
            const $allElements = $component.find('*').filter(function() {
                const $el = $(this);
                return $el.children().length === 0 && $el.text().trim().length > 0;
            });
            if ($allElements.length > 0) {
                $textArea = $allElements.first();
                console.log('Found text-containing element as fallback:', $textArea.prop('tagName'));
            }
        }
        
        if ($textArea.length === 0) {
            console.error('Could not find text area inside component!');
            console.log('Component structure:', $component[0]);
            copyToClipboard(content);
            showNotification('Could not find text element. Content copied to clipboard.', 'info');
            return;
        }

        // Insert the content
        try {
            // Highlight the component briefly
            $component.addClass('content-inserting');
            console.log('Inserting content into element:', $textArea[0]);
            
            setTimeout(function() {
                try {
                    let contentSet = false;
                    const originalContent = $textArea.text();
                    
                    console.log('Current content:', originalContent);
                    console.log('New content to set:', content);
                    
                    // Set the content - use text() for visible change
                    $textArea.text(content);
                    contentSet = true;
                    
                    // Force DOM update
                    $textArea[0].textContent = content;
                    
                    console.log('Content after setting:', $textArea.text());
                    console.log('Content set successfully!');
                    
                    // Highlight to show it worked
                    $component.removeClass('content-inserting');
                    $component.addClass('content-inserted');
                    $textArea.css({
                        'background-color': 'rgba(40, 167, 69, 0.2)',
                        'transition': 'background-color 0.5s'
                    });
                    
                    setTimeout(function() {
                        $component.removeClass('content-inserted');
                        $textArea.css('background-color', '');
                    }, 2000);
                    
                    // Copy to clipboard as backup
                    copyToClipboard(content);
                    
                    // Show clear message about what happened
                    showNotification(`✨ Content updated! Old: "${originalContent.substring(0, 20)}..." → New: "${content.substring(0, 20)}..."`, 'success');
                    
                } catch (innerError) {
                    console.error('Error during content insertion:', innerError);
                    copyToClipboard(content);
                    showNotification('Content copied to clipboard. Please paste manually into the component.', 'info');
                }
            }, 300);
            
        } catch (error) {
            console.error('Error applying content:', error);
            showNotification('Failed to apply content: ' + error.message, 'error');
        }
    }

    // ============= END COMPONENT SELECTOR =============

    function initCopilot() {
        // Check if already initialized
        if (document.getElementById('ai-copilot-button')) {
            return;
        }

        console.log('Initializing AEM Content Intelligence Co-Pilot...');

        // Create Co-Pilot UI
        createCopilotUI();

        // Check health status
        checkHealth();

        // Bind event listeners
        bindEvents();

        // Extract page context
        extractPageContext();
    }

    // Extract page context from AEM editor
    function extractPageContext() {
        const context = {
            title: getPageTitle(),
            content: getPageContent(),
            metaDescription: getMetaDescription(),
            componentCount: getComponentCount(),
            imagesWithoutAlt: getImagesWithoutAlt()
        };

        console.log('Page Context Extracted:', context);
        window.pageContext = context;
        
        // Update display after a short delay to ensure DOM is ready
        setTimeout(() => {
            updatePageInfoDisplay(context);
        }, 100);
        
        return context;
    }

    function getPageTitle() {
        // Try to get page title from various sources
        let title = '';
        
        // From page properties panel
        if (Granite && Granite.author && Granite.author.ContentFrame) {
            const path = Granite.author.ContentFrame.currentPath || window.location.pathname;
            // Extract page name from path
            title = path.split('/').pop() || '';
        }
        
        // From document title
        if (!title) {
            title = document.title.split('|')[0].trim();
        }
        
        return title || 'Untitled Page';
    }

    function getPageContent() {
        // Get all text content from the page
        const contentFrame = document.querySelector('iframe#ContentFrame');
        if (contentFrame && contentFrame.contentDocument) {
            const body = contentFrame.contentDocument.body;
            // Get text from main content area, exclude nav/footer
            const mainContent = body.querySelector('main') || body;
            return mainContent.innerText.substring(0, 500) + '...';
        }
        return '';
    }

    function getMetaDescription() {
        const contentFrame = document.querySelector('iframe#ContentFrame');
        if (contentFrame && contentFrame.contentDocument) {
            const metaTag = contentFrame.contentDocument.querySelector('meta[name="description"]');
            return metaTag ? metaTag.content : '';
        }
        return '';
    }

    function getComponentCount() {
        // Count editable components on the page
        const editables = document.querySelectorAll('.cq-Editable');
        return editables.length;
    }

    function getImagesWithoutAlt() {
        const contentFrame = document.querySelector('iframe#ContentFrame');
        if (contentFrame && contentFrame.contentDocument) {
            const images = contentFrame.contentDocument.querySelectorAll('img');
            let count = 0;
            images.forEach(img => {
                if (!img.alt || img.alt.trim() === '') {
                    count++;
                }
            });
            return count;
        }
        return 0;
    }

    function createCopilotUI() {
        const copilotHTML = `
            <!-- Floating AI Button -->
            <button id="ai-copilot-button" title="AI Content Co-Pilot"></button>

            <!-- AI Co-Pilot Panel -->
            <div id="ai-copilot-panel">
                <!-- Header -->
                <div class="copilot-header">
                    <h2>AI Content Co-Pilot</h2>
                    <button class="copilot-close">×</button>
                </div>

                <!-- Health Status -->
                <div class="copilot-health">
                    <span class="health-status" id="health-status">
                        <span id="health-text">Checking...</span>
                    </span>
                </div>

                <!-- Page Context Info -->
                <div class="copilot-page-info" id="page-info">
                    <div class="page-info-header">
                        <strong>📄 Current Page</strong>
                        <button class="btn-link" id="refresh-context-btn" title="Refresh page context">🔄</button>
                    </div>
                    <div class="page-info-content">
                        <div class="info-item">
                            <span class="info-label">Title:</span>
                            <span class="info-value" id="page-title-display">Loading...</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">Components:</span>
                            <span class="info-value" id="page-components-display">-</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">Images without alt:</span>
                            <span class="info-value" id="page-images-display">-</span>
                        </div>
                    </div>
                    <button class="btn btn-secondary btn-sm btn-block" id="analyze-page-btn">
                        🔍 Analyze This Page
                    </button>
                </div>

                <!-- Component Selector Modal -->
                <div id="component-selector-modal" class="modal-overlay" style="display: none;">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h3>✨ Apply to Component</h3>
                            <button class="btn-close modal-close">×</button>
                        </div>
                        <div class="modal-body">
                            <p class="modal-description">Select a component to insert the generated content:</p>
                            <div id="component-list" class="component-list"></div>
                        </div>
                    </div>
                </div>

                <!-- Tabs -->
                <div class="copilot-tabs">
                    <button class="copilot-tab active" data-tab="generate">Generate</button>
                    <button class="copilot-tab" data-tab="seo">SEO</button>
                    <button class="copilot-tab" data-tab="variations">Variations</button>
                    <button class="copilot-tab" data-tab="suggest">Suggest</button>
                    <button class="copilot-tab" data-tab="predict">Predict</button>
                </div>

                <!-- Content Area -->
                <div class="copilot-content">
                    <!-- Generate Tab -->
                    <div class="tab-content active" data-tab="generate">
                        <div class="form-group">
                            <label>What do you want to create?</label>
                            <select id="generate-type">
                                <option value="headlines">Headlines</option>
                                <option value="bodycopy">Body Copy</option>
                                <option value="metadescription">Meta Description</option>
                                <option value="alttext">Alt Text</option>
                            </select>
                        </div>

                        <div class="form-group" id="context-group">
                            <label>Context / Topic</label>
                            <textarea id="generate-context" placeholder="Describe your content topic or provide context..."></textarea>
                        </div>

                        <div class="form-group" id="tone-group" style="display:none;">
                            <label>Tone</label>
                            <select id="generate-tone">
                                <option value="professional">Professional</option>
                                <option value="casual">Casual</option>
                                <option value="creative">Creative</option>
                                <option value="technical">Technical</option>
                            </select>
                        </div>

                        <button class="btn btn-primary btn-block" id="generate-btn">
                            ✨ Generate Content
                        </button>

                        <div id="generate-results"></div>
                    </div>

                    <!-- SEO Tab -->
                    <div class="tab-content" data-tab="seo">
                        <div class="form-group">
                            <label>Page Title</label>
                            <input type="text" id="seo-title" placeholder="Enter page title...">
                        </div>

                        <div class="form-group">
                            <label>Page Content</label>
                            <textarea id="seo-content" placeholder="Paste your page content..."></textarea>
                        </div>

                        <div class="form-group">
                            <label>Meta Description</label>
                            <input type="text" id="seo-meta" placeholder="Enter meta description...">
                        </div>

                        <button class="btn btn-primary btn-block" id="seo-analyze-btn">
                            🎯 Analyze SEO
                        </button>

                        <div id="seo-results"></div>
                    </div>

                    <!-- Variations Tab -->
                    <div class="tab-content" data-tab="variations">
                        <div class="form-group">
                            <label>Original Content</label>
                            <textarea id="variation-content" placeholder="Enter content to adapt..."></textarea>
                        </div>

                        <div class="form-group">
                            <label>Target Audience</label>
                            <div class="audience-chips">
                                <div class="audience-chip" data-audience="enterprise">Enterprise</div>
                                <div class="audience-chip" data-audience="smb">SMB</div>
                                <div class="audience-chip" data-audience="b2c">B2C</div>
                                <div class="audience-chip" data-audience="technical">Technical</div>
                                <div class="audience-chip" data-audience="executive">Executive</div>
                            </div>
                        </div>

                        <button class="btn btn-primary btn-block" id="variation-btn" disabled>
                            🎭 Generate Variation
                        </button>

                        <div id="variation-results"></div>
                    </div>

                    <!-- Suggest Tab -->
                    <div class="tab-content" data-tab="suggest">
                        <div class="form-group">
                            <label>Page Type</label>
                            <select id="suggest-page-type">
                                <option value="product">Product Page</option>
                                <option value="landing">Landing Page</option>
                                <option value="article">Article Page</option>
                                <option value="default">General Page</option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label>Current Components (comma-separated)</label>
                            <input type="text" id="suggest-components" placeholder="e.g., hero, features, testimonials">
                        </div>

                        <button class="btn btn-primary btn-block" id="suggest-btn">
                            💡 Suggest Next Component
                        </button>

                        <div id="suggest-results"></div>
                    </div>

                    <!-- Predict Tab -->
                    <div class="tab-content" data-tab="predict">
                        <div class="form-group">
                            <label>Content to Analyze</label>
                            <textarea id="predict-content" placeholder="Paste content to predict performance..."></textarea>
                        </div>

                        <div class="form-group">
                            <label>Content Type</label>
                            <select id="predict-type">
                                <option value="general">General</option>
                                <option value="product">Product</option>
                                <option value="article">Article</option>
                                <option value="landing">Landing Page</option>
                            </select>
                        </div>

                        <button class="btn btn-primary btn-block" id="predict-btn">
                            📊 Predict Performance
                        </button>

                        <div id="predict-results"></div>
                    </div>
                </div>
            </div>
        `;

        $('body').append(copilotHTML);
    }

    function bindEvents() {
        // Toggle panel
        $(document).on('click', '#ai-copilot-button', function() {
            $('#ai-copilot-panel').toggleClass('active');
        });

        $(document).on('click', '.copilot-close', function() {
            $('#ai-copilot-panel').removeClass('active');
        });

        // Tab switching
        $(document).on('click', '.copilot-tab', function() {
            const tab = $(this).data('tab');
            $('.copilot-tab').removeClass('active');
            $(this).addClass('active');
            $('.tab-content').removeClass('active');
            $(`.tab-content[data-tab="${tab}"]`).addClass('active');
        });

        // Generate type change
        $(document).on('change', '#generate-type', function() {
            const type = $(this).val();
            updateGenerateForm(type);
        });

        // Generate button
        $(document).on('click', '#generate-btn', function() {
            handleGenerate();
        });

        // Analyze Page button
        $(document).on('click', '#analyze-page-btn', function() {
            analyzeCurrentPage();
        });

        // Refresh context button
        $(document).on('click', '#refresh-context-btn', function() {
            const context = extractPageContext();
            updatePageInfoDisplay(context);
            showNotification('Page context refreshed!', 'success');
        });

        // SEO analyze button
        $(document).on('click', '#seo-analyze-btn', function() {
            handleSEOAnalyze();
        });

        // Audience selection
        $(document).on('click', '.audience-chip', function() {
            $('.audience-chip').removeClass('selected');
            $(this).addClass('selected');
            $('#variation-btn').prop('disabled', false);
        });

        // Variation button
        $(document).on('click', '#variation-btn', function() {
            handleVariation();
        });

        // Suggest button
        $(document).on('click', '#suggest-btn', function() {
            handleSuggest();
        });

        // Predict button
        $(document).on('click', '#predict-btn', function() {
            handlePredict();
        });

        // Copy result
        $(document).on('click', '.btn-copy', function() {
            const text = $(this).closest('.result-item').find('.result-text').text();
            copyToClipboard(text);
            showSuccess('Copied to clipboard!');
        });

        // Apply to page button
        $(document).on('click', '.btn-apply', function() {
            const content = $(this).data('content');
            const type = $(this).data('type');
            showComponentSelector(content, type);
        });

        // Modal close
        $(document).on('click', '.modal-close, .modal-overlay', function(e) {
            if (e.target === e.currentTarget) {
                $('.modal-overlay').hide();
            }
        });

        // Component selection
        $(document).on('click', '.component-option', function() {
            const componentPath = $(this).data('path');
            const componentType = $(this).data('type');
            const content = $('#component-selector-modal').data('content');
            const contentType = $('#component-selector-modal').data('contentType');
            
            applyContentToComponent(content, contentType, componentPath, componentType);
            $('.modal-overlay').hide();
        });
    }

    function checkHealth() {
        window.ContentIntelligenceAPI.checkHealth().then(function(result) {
            const statusEl = $('#health-status');
            const textEl = $('#health-text');

            if (result.success && result.ollamaAvailable) {
                statusEl.removeClass('degraded').addClass('healthy');
                textEl.text('AI Ready');
            } else {
                statusEl.removeClass('healthy').addClass('degraded');
                textEl.text('Ollama Offline');
                if (result.hint) {
                    console.warn('Ollama hint:', result.hint);
                }
            }
        });
    }

    function updateGenerateForm(type) {
        const contextLabel = $('#context-group label');
        const toneGroup = $('#tone-group');

        switch(type) {
            case 'headlines':
                contextLabel.text('Context / Topic');
                $('#generate-context').attr('placeholder', 'Describe your content topic...');
                toneGroup.hide();
                break;
            case 'bodycopy':
                contextLabel.text('Topic');
                $('#generate-context').attr('placeholder', 'What should the content be about?');
                toneGroup.show();
                break;
            case 'metadescription':
                contextLabel.text('Page Content Summary');
                $('#generate-context').attr('placeholder', 'Summarize your page content...');
                toneGroup.hide();
                break;
            case 'alttext':
                contextLabel.text('Image Description');
                $('#generate-context').attr('placeholder', 'Describe what the image shows...');
                toneGroup.hide();
                break;
        }
    }

    async function handleGenerate() {
        const type = $('#generate-type').val();
        const context = $('#generate-context').val().trim();
        const tone = $('#generate-tone').val();
        const btn = $('#generate-btn');
        const resultsDiv = $('#generate-results');

        if (!context) {
            showError(resultsDiv, 'Please provide context or topic');
            return;
        }

        btn.addClass('loading').prop('disabled', true);
        resultsDiv.empty();

        try {
            let result;
            
            switch(type) {
                case 'headlines':
                    result = await window.ContentIntelligenceAPI.generateHeadlines(context, 3);
                    if (result.success) {
                        displayHeadlines(result.headlines, resultsDiv);
                    }
                    break;
                case 'bodycopy':
                    result = await window.ContentIntelligenceAPI.generateBodyCopy(context, tone, 200);
                    if (result.success) {
                        displaySingleResult(result.content, resultsDiv);
                    }
                    break;
                case 'metadescription':
                    result = await window.ContentIntelligenceAPI.generateMetaDescription(context);
                    if (result.success) {
                        displaySingleResult(result.metaDescription, resultsDiv);
                    }
                    break;
                case 'alttext':
                    result = await window.ContentIntelligenceAPI.generateAltText(context);
                    if (result.success) {
                        displaySingleResult(result.altText, resultsDiv);
                    }
                    break;
            }

            if (!result.success) {
                showError(resultsDiv, result.error || 'Generation failed');
            }
        } catch (error) {
            showError(resultsDiv, error.message);
        } finally {
            btn.removeClass('loading').prop('disabled', false);
        }
    }

    async function handleSEOAnalyze() {
        const title = $('#seo-title').val().trim();
        const content = $('#seo-content').val().trim();
        const meta = $('#seo-meta').val().trim();
        const btn = $('#seo-analyze-btn');
        const resultsDiv = $('#seo-results');

        btn.addClass('loading').prop('disabled', true);
        resultsDiv.empty();

        try {
            const result = await window.ContentIntelligenceAPI.analyzeSEO(title, content, meta);
            
            if (result.success) {
                displaySEOResults(result.analysis, resultsDiv);
            } else {
                showError(resultsDiv, result.error || 'Analysis failed');
            }
        } catch (error) {
            showError(resultsDiv, error.message);
        } finally {
            btn.removeClass('loading').prop('disabled', false);
        }
    }

    async function handleVariation() {
        const content = $('#variation-content').val().trim();
        const audience = $('.audience-chip.selected').data('audience');
        const btn = $('#variation-btn');
        const resultsDiv = $('#variation-results');

        if (!content) {
            showError(resultsDiv, 'Please provide content');
            return;
        }

        btn.addClass('loading').prop('disabled', true);
        resultsDiv.empty();

        try {
            const result = await window.ContentIntelligenceAPI.generateVariation(content, audience);
            
            if (result.success) {
                displayVariationResult(result.variation, result.audience, resultsDiv);
            } else {
                showError(resultsDiv, result.error || 'Generation failed');
            }
        } catch (error) {
            showError(resultsDiv, error.message);
        } finally {
            btn.removeClass('loading').prop('disabled', false);
            $('#variation-btn').prop('disabled', false);
        }
    }

    async function handleSuggest() {
        const pageType = $('#suggest-page-type').val();
        const componentsStr = $('#suggest-components').val().trim();
        const components = componentsStr ? componentsStr.split(',').map(c => c.trim()) : [];
        const btn = $('#suggest-btn');
        const resultsDiv = $('#suggest-results');

        btn.addClass('loading').prop('disabled', true);
        resultsDiv.empty();

        try {
            const result = await window.ContentIntelligenceAPI.suggestComponent(components, pageType);
            
            if (result.success) {
                displayComponentSuggestion(result.suggestion, resultsDiv);
            } else {
                showError(resultsDiv, result.error || 'Suggestion failed');
            }
        } catch (error) {
            showError(resultsDiv, error.message);
        } finally {
            btn.removeClass('loading').prop('disabled', false);
        }
    }

    async function handlePredict() {
        const content = $('#predict-content').val().trim();
        const contentType = $('#predict-type').val();
        const btn = $('#predict-btn');
        const resultsDiv = $('#predict-results');

        if (!content) {
            showError(resultsDiv, 'Please provide content to analyze');
            return;
        }

        btn.addClass('loading').prop('disabled', true);
        resultsDiv.empty();

        try {
            const result = await window.ContentIntelligenceAPI.predictPerformance(content, contentType);
            
            if (result.success) {
                displayPrediction(result.prediction, resultsDiv);
            } else {
                showError(resultsDiv, result.error || 'Prediction failed');
            }
        } catch (error) {
            showError(resultsDiv, error.message);
        } finally {
            btn.removeClass('loading').prop('disabled', false);
        }
    }

    function displayHeadlines(headlines, container) {
        container.empty();
        const $resultBox = $('<div class="result-box"></div>');
        
        headlines.forEach(function(headline, index) {
            const $item = $('<div class="result-item"></div>');
            const $text = $('<div class="result-text"></div>').text(headline);
            const $actions = $('<div class="result-actions"></div>');
            
            const $copyBtn = $('<button class="btn-icon btn-copy" title="Copy">📋</button>');
            const $applyBtn = $('<button class="btn-icon btn-apply" title="Apply to Page">✨</button>')
                .data('content', headline)
                .data('type', 'headline');
            
            $actions.append($copyBtn, $applyBtn);
            $item.append($text, $actions);
            $resultBox.append($item);
        });
        
        container.append($resultBox);
    }

    function displaySingleResult(content, container, type = 'text') {
        container.empty();
        const $resultBox = $('<div class="result-box"></div>');
        const $item = $('<div class="result-item"></div>');
        const $text = $('<div class="result-text"></div>').text(content);
        const $actions = $('<div class="result-actions"></div>');
        
        const $copyBtn = $('<button class="btn-icon btn-copy" title="Copy">📋</button>');
        const $applyBtn = $('<button class="btn-icon btn-apply" title="Apply to Page">✨</button>')
            .data('content', content)
            .data('type', type);
        
        $actions.append($copyBtn, $applyBtn);
        $item.append($text, $actions);
        $resultBox.append($item);
        container.append($resultBox);
    }

    function displaySEOResults(analysis, container) {
        const html = `
            <div class="seo-score">
                <div class="score-circle">${analysis.score}</div>
                <div class="score-grade">Grade: ${analysis.grade}</div>
                <div style="color: #666; font-size: 13px;">${analysis.status.toUpperCase()}</div>
            </div>
            ${analysis.suggestions.length > 0 ? `
                <div class="result-box">
                    <strong style="display: block; margin-bottom: 12px;">Suggestions:</strong>
                    <ul class="suggestions-list">
                        ${analysis.suggestions.map(s => `<li>• ${escapeHtml(s)}</li>`).join('')}
                    </ul>
                </div>
            ` : '<div class="success-message">Perfect! Your content is fully optimized.</div>'}
        `;
        container.html(html);
    }

    function displayVariationResult(variation, audience, container) {
        const html = `
            <div class="result-box">
                <strong style="display: block; margin-bottom: 12px;">
                    ${audience.charAt(0).toUpperCase() + audience.slice(1)} Version:
                </strong>
                <div class="result-item">
                    <div class="result-text">${escapeHtml(variation)}</div>
                    <div class="result-actions">
                        <button class="btn-icon btn-copy" title="Copy">📋</button>
                    </div>
                </div>
            </div>
        `;
        container.html(html);
    }

    function displayComponentSuggestion(suggestion, container) {
        const html = `
            <div class="component-card">
                <div class="component-name">💡 ${suggestion.component}</div>
                <div class="component-reason">${escapeHtml(suggestion.reason)}</div>
                <div style="margin-top: 12px; font-size: 12px; color: #999;">
                    Confidence: ${suggestion.confidence}
                </div>
            </div>
        `;
        container.html(html);
    }

    function displayPrediction(prediction, container) {
        const html = `
            <div class="prediction-box">
                <div class="prediction-score">${prediction.engagementScore}/100</div>
                <div class="prediction-label">Predicted Engagement Score</div>
                <div class="prediction-details">
                    <div class="prediction-stat">
                        <div class="prediction-stat-value">${prediction.predictedCTR}</div>
                        <div class="prediction-stat-label">Click-Through Rate</div>
                    </div>
                    <div class="prediction-stat">
                        <div class="prediction-stat-value">${prediction.confidence}</div>
                        <div class="prediction-stat-label">Confidence</div>
                    </div>
                </div>
            </div>
            <div class="result-box" style="margin-top: 16px;">
                <strong style="display: block; margin-bottom: 8px;">Recommendation:</strong>
                <p style="margin: 0; font-size: 14px; line-height: 1.6;">${escapeHtml(prediction.recommendation)}</p>
            </div>
        `;
        container.html(html);
    }

    function showError(container, message) {
        container.html(`<div class="error-message">${escapeHtml(message)}</div>`);
    }

    function showSuccess(message) {
        // Simple console log for now
        console.log(message);
    }

    function copyToClipboard(text) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Update page info display
    function updatePageInfoDisplay(context) {
        if (!context) {
            context = window.pageContext || extractPageContext();
        }

        $('#page-title-display').text(context.title);
        $('#page-components-display').text(context.componentCount);
        $('#page-images-display').text(context.imagesWithoutAlt);

        // Auto-fill context field with page title if empty
        const contextField = $('#generate-context');
        if (!contextField.val() || contextField.val().trim() === '') {
            contextField.val(context.title);
        }
    }

    // Analyze current page and auto-fill all forms
    function analyzeCurrentPage() {
        const context = extractPageContext();
        
        // Switch to SEO tab and auto-fill
        $('.copilot-tab[data-tab="seo"]').click();
        
        // Fill SEO form with page content
        $('#seo-title').val(context.title);
        $('#seo-content').val(context.content);
        $('#seo-meta').val(context.metaDescription);
        
        // Show notification
        showNotification(`Page analyzed! Found ${context.componentCount} components and ${context.imagesWithoutAlt} images without alt text.`, 'success');
        
        // Auto-run SEO analysis
        setTimeout(() => {
            handleSEOAnalyze();
        }, 500);
    }

    // Show notification
    function showNotification(message, type = 'info') {
        const notification = $('<div class="notification"></div>')
            .addClass(`notification-${type}`)
            .text(message);
        
        $('body').append(notification);
        
        setTimeout(() => {
            notification.addClass('show');
        }, 100);
        
        setTimeout(() => {
            notification.removeClass('show');
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }

})(window.jQuery || window.Granite.$, document);

