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

    // Helper: toggle panel with slide-in/out animation and notifications
    function togglePanel(forceOpen) {
        const $panel = $('#ai-copilot-panel');
        const isActive = $panel.hasClass('active');
        const shouldOpen = (typeof forceOpen === 'boolean') ? forceOpen : !isActive;
        if (shouldOpen) {
            $panel.removeClass('closing').addClass('active');
            showNotification('Co-Pilot opened', 'info');
        } else {
            if (!$panel.hasClass('active')) return;
            $panel.addClass('closing');
            setTimeout(() => {
                $panel.removeClass('active closing');
            }, 250);
            showNotification('Co-Pilot closed', 'info');
        }
    }

    // Helper: apply last viewed tab from localStorage
    function applyLastTab() {
        try {
            const lastTab = localStorage.getItem('copilot:lastTab') || 'generate';
            const $tabBtn = $(`.copilot-tab[data-tab="${lastTab}"]`);
            if ($tabBtn.length) {
                $tabBtn.click();
            }
        } catch (e) {}
    }

    // Helper: init draggable floating button with position persistence
    function initDraggableButton() {
        const $btn = $('#ai-copilot-button');
        let isDragging = false;
        let startX = 0, startY = 0;
        let origLeft = null, origTop = null;

        // Restore saved position
        try {
            const saved = JSON.parse(localStorage.getItem('copilot:btnPos') || '{}');
            if (typeof saved.left === 'number' && typeof saved.top === 'number') {
                $btn.css({ left: saved.left + 'px', top: saved.top + 'px', right: 'auto', bottom: 'auto' });
            }
        } catch (e) {}

        const onMove = (clientX, clientY) => {
            if (!isDragging) return;
            const dx = clientX - startX;
            const dy = clientY - startY;
            const newLeft = Math.max(8, Math.min(window.innerWidth - 68, origLeft + dx));
            const newTop = Math.max(8, Math.min(window.innerHeight - 68, origTop + dy));
            $btn.css({ left: newLeft + 'px', top: newTop + 'px', right: 'auto', bottom: 'auto' });
        };

        const savePos = () => {
            try {
                const rect = $btn[0].getBoundingClientRect();
                localStorage.setItem('copilot:btnPos', JSON.stringify({ left: rect.left, top: rect.top }));
            } catch (e) {}
        };

        $btn.on('mousedown', function(e) {
            if (e.button !== 0) return; // left click only
            isDragging = true;
            $btn.addClass('dragging');
            startX = e.clientX;
            startY = e.clientY;
            const rect = $btn[0].getBoundingClientRect();
            origLeft = rect.left;
            origTop = rect.top;
            e.preventDefault();
        });

        $(document).on('mousemove', function(e) {
            if (!isDragging) return;
            onMove(e.clientX, e.clientY);
        });

        $(document).on('mouseup', function() {
            if (isDragging) {
                isDragging = false;
                $btn.removeClass('dragging');
                savePos();
            }
        });

        // Touch support
        $btn.on('touchstart', function(e) {
            const t = e.originalEvent.touches[0];
            isDragging = true;
            $btn.addClass('dragging');
            startX = t.clientX;
            startY = t.clientY;
            const rect = $btn[0].getBoundingClientRect();
            origLeft = rect.left;
            origTop = rect.top;
        });
        $(document).on('touchmove', function(e) {
            if (!isDragging) return;
            const t = e.originalEvent.touches[0];
            onMove(t.clientX, t.clientY);
        });
        $(document).on('touchend touchcancel', function() {
            if (isDragging) {
                isDragging = false;
                $btn.removeClass('dragging');
                savePos();
            }
        });
    }

    // Keyboard shortcut: Cmd/Ctrl + K to toggle
    $(document).on('keydown', function(e) {
        const isK = (e.key && e.key.toLowerCase() === 'k');
        if (isK && (e.metaKey || e.ctrlKey)) {
            e.preventDefault();
            togglePanel();
        }
    });

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

        // Insert the content directly (previous behavior)
        try {
            // Highlight the component briefly
            $component.addClass('content-inserting');
            console.log('Inserting content into element:', $textArea[0]);

            setTimeout(function() {
                try {
                    const originalContent = $textArea.text();

                    console.log('Current content:', originalContent);
                    console.log('New content to set:', content);

                    // Set the content - use text() for visible change
                    $textArea.text(content);

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
                    showNotification(`✨ Content updated! Old: "${(original-content||originalContent||'').toString().substring(0, 20)}..." → New: "${(content||'').toString().substring(0, 20)}..."`, 'success');
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

        // Enhance UX: last tab + draggable button
        applyLastTab();
        initDraggableButton();

		// Initialize Generate preset (segmented control)
		initGeneratePreset();
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
		// Count only content-bearing components by resourceType (WKND)
		const TARGET_RESOURCE_TYPES = new Set([
			'wknd/components/text',
			'wknd/components/title',
			'wknd/components/image',
			'wknd/components/teaser'
		]);

		// Prefer counting components within the editor ContentFrame (actual page DOM)
		const frameSelectors = ['iframe#ContentFrame', 'iframe[name="ContentFrame"]', 'iframe.editor-Frame'];
		let contentDoc = null;
		for (let sel of frameSelectors) {
			const frame = document.querySelector(sel);
			if (frame && frame.contentDocument) {
				contentDoc = frame.contentDocument;
				break;
			}
		}

		try {
			if (contentDoc) {
				// Find components whose data layer encodes resourceType, or wrappers with path and resource hint
				const uniquePaths = new Set();

				// 1) Parse Core Components data layer attribute if present
				const dataLayerNodes = contentDoc.querySelectorAll('[data-cmp-data-layer]');
				for (let i = 0; i < dataLayerNodes.length; i++) {
					const node = dataLayerNodes[i];
					const dl = node.getAttribute('data-cmp-data-layer');
					if (!dl) continue;
					try {
						// Some components put a JSON object keyed by id; handle both stringified object and direct object
						const parsed = JSON.parse(dl);
						// Inspect values for a resourceType-like field
						const entries = Array.isArray(parsed) ? parsed : Object.values(parsed);
						for (let j = 0; j < entries.length; j++) {
				 			const v = entries[j];
				 			// Common shapes: { '@type': 'wknd/components/text', ... } or { 'xdm:component': 'wknd/components/text', ... }
				 			const rt = (v && (v['@type'] || v['xdm:component'] || v['resourceType'])) || '';
				 			if (rt && TARGET_RESOURCE_TYPES.has(rt)) {
				 				const container = node.closest('[data-cmp-path],[data-path]') || node;
				 				const path = (container.getAttribute && (container.getAttribute('data-cmp-path') || container.getAttribute('data-path'))) || '';
				 				if (path && path.includes('/jcr:content/')) {
				 					uniquePaths.add(path);
				 				}
				 			}
						}
					} catch (_e) {
						// ignore parse errors
					}
				}

				// 2) Heuristic: match by resourceType in common author attributes if present
				if (uniquePaths.size === 0) {
					const candidates = contentDoc.querySelectorAll('[data-resource-type],[data-resourcetype],[data-rt],[data-component]');
					for (let i = 0; i < candidates.length; i++) {
						const el = candidates[i];
						const rt = el.getAttribute('data-resource-type')
							|| el.getAttribute('data-resourcetype')
							|| el.getAttribute('data-rt')
							|| el.getAttribute('data-component')
							|| '';
						if (rt && TARGET_RESOURCE_TYPES.has(rt)) {
							const container = el.closest('[data-cmp-path],[data-path]') || el;
							const path = (container.getAttribute && (container.getAttribute('data-cmp-path') || container.getAttribute('data-path'))) || '';
							if (path && path.includes('/jcr:content/')) {
								uniquePaths.add(path);
							}
						}
					}
				}

				// 3) Last resort within ContentFrame: infer by resourceType suffix combined with data-path
				if (uniquePaths.size === 0) {
					const names = [
						['/text', ['.cmp-text', '[data-cmp-is="text"]']],
						['/title', ['.cmp-title', '[data-cmp-is="title"]']],
						['/image', ['.cmp-image', '[data-cmp-is="image"]']],
						['/teaser', ['.cmp-teaser', '[data-cmp-is="teaser"]']]
					];
					for (let k = 0; k < names.length; k++) {
						const selList = names[k][1];
						for (let s = 0; s < selList.length; s++) {
							const nodes = contentDoc.querySelectorAll(selList[s]);
							for (let n = 0; n < nodes.length; n++) {
								const container = nodes[n].closest('[data-cmp-path],[data-path]') || nodes[n];
								const path = (container.getAttribute && (container.getAttribute('data-cmp-path') || container.getAttribute('data-path'))) || '';
								if (path && path.includes('/jcr:content/')) {
									uniquePaths.add(path);
								}
							}
						}
					}
				}

				if (uniquePaths.size > 0) return uniquePaths.size;
			}
		} catch (e) {
			// ignore and try overlay-based fallback
		}

		// Overlay-based fallback: use Granite author overlay metadata
		try {
			const overlaySet = new Set();
			// Prefer overlays that expose resource type
			const overlays = document.querySelectorAll('.cq-Overlay[data-path]');
			for (let i = 0; i < overlays.length; i++) {
				const ov = overlays[i];
				const path = ov.getAttribute('data-path') || '';
				if (!path || !path.includes('/jcr:content/')) continue;
				const rt = ov.getAttribute('data-resource-type') || ov.getAttribute('data-resourcetype') || '';
				if (rt && TARGET_RESOURCE_TYPES.has(rt)) {
					overlaySet.add(path);
				}
			}
			// If overlays don't expose resource type, try Granite author API
			if (overlaySet.size === 0 && window.Granite && window.Granite.author && Array.isArray(window.Granite.author.editables)) {
				const editables = window.Granite.author.editables;
				for (let i = 0; i < editables.length; i++) {
					const ed = editables[i];
					const path = ed && (ed.path || (ed.overlay && ed.overlay.dataset && ed.overlay.dataset.path)) || '';
					const rt = ed && (ed.type || (ed.overlay && ed.overlay.dataset && (ed.overlay.dataset.resourceType || ed.overlay.dataset.resourcetype))) || '';
					if (path && rt && path.includes('/jcr:content/') && TARGET_RESOURCE_TYPES.has(rt)) {
						overlaySet.add(path);
					}
				}
			}
			if (overlaySet.size > 0) return overlaySet.size;
		} catch (_e) {}

		return 0;
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
                    <button class="copilot-tab" data-tab="brand">Brand Voice</button>
                    <button class="copilot-tab" data-tab="vision">Vision AI</button>
                    <button class="copilot-tab" data-tab="seo">SEO</button>
                    <button class="copilot-tab" data-tab="variations">Variations</button>
                    <button class="copilot-tab" data-tab="suggest">Suggest</button>
                    <button class="copilot-tab" data-tab="predict">Predict</button>
                </div>

                <!-- Content Area -->
                <div class="copilot-content">
                    <!-- Generate Tab -->
                    <div class="tab-content active" data-tab="generate">
                        <div class="generate-layout">
                            <div class="generate-controls">
                                <div class="form-group">
                                    <label>What do you want to create?</label>
                                    <select id="generate-preset-select">
                                        <argument>Headlines</argument>
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

                                <div class="form-group" id="count-group" style="display:none;">
                                    <label>Number of Headlines</label>
                                    <input type="number" id="generate-count" min="1" max="10" value="3"/>
                                </div>

                                <div class="form-group" id="length-group" style="display:none;">
                                    <label>Length (words)</label>
                                    <input type="range" id="generate-length" min="50" max="400" step="50" value="200"/>
                                    <div style="font-size:12px;color:#666;margin-top:4px;">Target: <span id="generate-length-value">200</span> words</div>
                                </div>

                                <div class="form-group" id="variant-group" style="display:none;">
                                    <label>Number of Variants</label>
                                    <input type="number" id="body-variant-count" min="1" max="5" value="2" style="width:100px;"/>
                                    <div class="tone-chips" style="margin-top:8px;">
                                        <span class="tone-chip" data-tone="professional" style="cursor:pointer;padding:4px 8px;border:1px solid #ccc;border-radius:12px;margin-right:6px;">Professional</span>
                                        <span class="tone-chip" data-tone="casual" style="cursor:pointer;padding:4px 8px;border:1px solid #ccc;border-radius:12px;margin-right:6px;">Casual</span>
                                        <span class="tone-chip" data-tone="creative" style="cursor:pointer;padding:4px 8px;border:1px solid #ccc;border-radius:12px;margin-right:6px;">Creative</span>
                                        <span class="tone-chip" data-tone="technical" style="cursor:pointer;padding:4px 8px;border:1px solid #ccc;border-radius:12px;">Technical</span>
                                    </div>
                                </div>

                                <button class="btn btn-primary btn-block" id="generate-btn">
                                    ✨ Generate Content
                                </button>
                                <button class="btn btn-secondary btn-block" id="generate-apply-btn" style="margin-top:8px;">
                                    ✨ Generate + Apply
                                </button>
                            </div>
                            <div class="generate-results-panel">
                                <div id="generate-results"></div>
                            </div>
                        </div>
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

                    <!-- Brand Voice Tab -->
                    <div class="tab-content" data-tab="brand">
                        <!-- Training Section -->
                        <div class="form-group">
                            <label>Brand Voice Training</label>
                            <div id="brand-status" class="info-item" style="display:flex;align-items:center;gap:8px;">
                                <span class="info-label">Status:</span>
                                <span class="info-value" id="brand-status-text">Not trained</span>
                            </div>
                        </div>
                        <button class="btn btn-secondary btn-block" id="brand-train-btn">
                                🧠 Train on WKND Content
                        </button>

                        <hr style="margin:16px 0; border:0; border-top:1px solid #eee;"/>

                        <!-- Testing Section -->
                        <div class="form-group">
                            <label>Topic</label>
                            <textarea id="brand-topic" placeholder="e.g., Announce our new hiking backpack with key benefits..."></textarea>
                        </div>
                        <div class="form-group">
                            <label>Content Type</label>
                            <select id="brand-content-type">
                                <option value="general">General</option>
                                <option value="product">Product</option>
                                <option value="article">Article</option>
                                <option value="landing">Landing Page</option>
                            </select>
                        </div>
                        <div class="form-group" style="display:flex;align-items:center;gap:8px;">
                            <input type="checkbox" id="brand-use-page-context"/>
                            <label for="brand-use-page-context" style="margin:0;">Use current page as context</label>
                        </div>
                        <button class="btn btn-primary btn-block" id="brand-compare-btn">
                                ⚖️ Compare Generic vs Brand Voice
                        </button>

                        <!-- Results -->
                        <div id="brand-results" style="margin-top:16px;"></div>
                    </div>

                    <!-- Vision AI Tab -->
                    <div class="tab-content" data-tab="vision">
                        <div class="form-group">
                            <label>DAM Image Path</label>
                            <input type="text" id="vision-image-path" placeholder="e.g., /content/dam/wknd/en/adventures/himalaya.jpg"/>
                        </div>
                        <div class="form-group">
                            <label>Analysis Type</label>
                            <select id="vision-analysis-type">
                                <option value="alt">Alt Text</option>
                                <option value="seo">SEO</option>
                                <option value="accessibility">Accessibility</option>
                                <option value="objects">Objects</option>
                            </select>
                        </div>
                        <button class="btn btn-primary btn-block" id="vision-analyze-btn">
                                👁️ Analyze Image
                        </button>
                        <div id="vision-results" style="margin-top:16px;"></div>
                    </div>

                   
                </div>
            </div>
        `;

        $('body').append(copilotHTML);
    }

    function bindEvents() {
        // Local state for Brand Voice
        let brandProfileId = null;

        // Toggle panel
        $(document).on('click', '#ai-copilot-button', function() {
            togglePanel();
        });

        $(document).on('click', '.copilot-close', function() {
            togglePanel(false);
        });

        // Tab switching
        $(document).on('click', '.copilot-tab', function() {
            const tab = $(this).data('tab');
            $('.copilot-tab').removeClass('active');
            $(this).addClass('active');
            $('.tab-content').removeClass('active');
            $(`.tab-content[data-tab="${tab}"]`).addClass('active');
            try { localStorage.setItem('copilot:lastTab', tab); } catch (e) {}
        });

        // Generate preset change (dropdown)
        $(document).on('change', '#generate-preset-select', function() {
            const preset = $(this).val();
            setGeneratePreset(preset, true);
        });

        // Generate button
        $(document).on('click', '#generate-btn', function() {
			handleGenerate(false);
		});

		// Generate + Apply button
		$(document).on('click', '#generate-apply-btn', function() {
			handleGenerate(true);
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

        // (removed) Structure tab/button

        // Vision AI: analyze image
        $(document).on('click', '#vision-analyze-btn', function() {
            handleVisionAnalyze();
        });

        // Brand voice: train
        $(document).on('click', '#brand-train-btn', async function() {
            const btn = $('#brand-train-btn');
            const statusText = $('#brand-status-text');
            btn.addClass('loading').prop('disabled', true);
            try {
                // Train on WKND content root
                const result = await window.ContentIntelligenceAPI.trainBrandVoice('/content/wknd/us');
                if (result && result.success && result.profileId) {
                    brandProfileId = result.profileId;
                    statusText.text(`Trained (Profile: ${brandProfileId.substring(0, 8)}...)`);
                    showNotification('Brand voice trained successfully!', 'success');
                } else {
                    const err = (result && result.error) ? result.error : 'Training failed';
                    showNotification(err, 'error');
                }
            } catch (e) {
                showNotification(e.message || 'Training error', 'error');
            } finally {
                btn.removeClass('loading').prop('disabled', false);
            }
        });

        // Brand voice: compare
        $(document).on('click', '#brand-compare-btn', async function() {
            const topic = $('#brand-topic').val().trim();
            const contentType = $('#brand-content-type').val();
            const usePageContext = $('#brand-use-page-context').is(':checked');
            const btn = $('#brand-compare-btn');
            const resultsDiv = $('#brand-results');
            if (!topic) {
                showError(resultsDiv, 'Please enter a topic');
                return;
            }
            btn.addClass('loading').prop('disabled', true);
            resultsDiv.empty();
            try {
                const prompt = `Topic: ${topic}\nContent Type: ${contentType}`;
                // Prefer passing profileId to use trained brand voice
                const payload = { action: 'brandvoice', prompt: prompt, maxTokens: 150 };
                if (brandProfileId) payload.profileId = brandProfileId;
                if (usePageContext && window.pageContext && window.pageContext.content) {
                    payload.context = window.pageContext.content;
                }
                const result = await window.ContentIntelligenceAPI._post('/generate', payload);
                if (result && result.success) {
                    displayBrandComparison({
                        generic: result.generic || '',
                        brand: result.brand || '',
                        profileId: result.profileId || brandProfileId || ''
                    }, resultsDiv);
                } else {
                    const err = (result && result.error) ? result.error : 'Comparison failed';
                    showError(resultsDiv, err);
                }
            } catch (e) {
                showError(resultsDiv, e.message || 'Comparison error');
            } finally {
                btn.removeClass('loading').prop('disabled', false);
            }
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
        // Apply preview modal actions
        $(document).on('click', '#apply-confirm-btn', function() {
            const stash = window._copilotPendingApply;
            if (!stash || !stash.element) { $('.modal-overlay').hide(); return; }
            try {
                const $el = $(stash.element);
                const $comp = $(stash.component || $el);
                const newText = String(stash.newContent || '');
                const originalContent = $el.text();
                $comp.addClass('content-inserting');
                setTimeout(function() {
                    try {
                        $el.text(newText);
                        $el[0].textContent = newText;
                        $comp.removeClass('content-inserting').addClass('content-inserted');
                        $el.css({'background-color':'rgba(40,167,69,0.2)','transition':'background-color 0.5s'});
                        setTimeout(function(){ $comp.removeClass('content-inserted'); $el.css('background-color',''); }, 2000);
                        copyToClipboard(newText);
                        showNotification(`Applied. Old: "${(originalContent||'').substring(0,20)}..." → New: "${newText.substring(0,20)}..."`,'success');
                    } catch(e){ console.error(e); }
                }, 100);
            } finally {
                $('.modal-overlay').hide();
                window._copilotPendingApply = null;
            }
        });
        $(document).on('click', '#apply-cancel-btn', function() {
            $('.modal-overlay').hide();
            window._copilotPendingApply = null;
        });

        // (removed) refinement buttons for body copy

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
		const countGroup = $('#count-group');
		const lengthGroup = $('#length-group');

        switch(type) {
            case 'headlines':
                contextLabel.text('Context / Topic');
                $('#generate-context').attr('placeholder', 'Describe your content topic...');
                toneGroup.show();
                countGroup.show();
                lengthGroup.hide();
                $('#variant-group').hide();
                break;
            case 'bodycopy':
                contextLabel.text('Topic');
                $('#generate-context').attr('placeholder', 'What should the content be about?');
                toneGroup.show();
				countGroup.hide();
				lengthGroup.show();
                $('#variant-group').hide();
                break;
            case 'metadescription':
                contextLabel.text('Page Content Summary');
                $('#generate-context').attr('placeholder', 'Summarize your page content...');
                toneGroup.hide();
				countGroup.hide();
				lengthGroup.hide();
                $('#variant-group').hide();
                break;
            case 'alttext':
                contextLabel.text('Image Description');
                $('#generate-context').attr('placeholder', 'Describe what the image shows...');
                toneGroup.hide();
				countGroup.hide();
				lengthGroup.hide();
                $('#variant-group').hide();
                break;
        }
    }

    async function handleGenerate(applyAfter = false) {
        const type = ($('#generate-preset-select').val()) || 'headlines';
        const context = $('#generate-context').val().trim();
        const tone = $('#generate-tone').val();
		const count = parseInt($('#generate-count').val() || '3', 10) || 3;
		const wordCount = parseInt($('#generate-length').val() || '200', 10) || 200;
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
			let contentToApply = null;
			let applyType = 'text';
            
            switch(type) {
                case 'headlines':
                    result = await window.ContentIntelligenceAPI.generateHeadlines(context, count);
                    if (result.success) {
                        displayHeadlines(result.headlines, resultsDiv);
						if (applyAfter && Array.isArray(result.headlines) && result.headlines.length > 0) {
							contentToApply = result.headlines[0];
							applyType = 'headline';
						}
                    }
                    break;
                case 'bodycopy': {
                    const r = await window.ContentIntelligenceAPI.generateBodyCopy(context, getSelectedTone(), wordCount);
                    if (r && r.success && r.content) {
                        displaySingleResult(r.content, resultsDiv, 'body');
                        if (applyAfter) {
                            contentToApply = r.content;
                            applyType = 'text';
                        }
                    } else {
                        showError(resultsDiv, (r && r.error) ? r.error : 'Generation failed');
                    }
                    break;
                }
                case 'metadescription': {
                    result = await window.ContentIntelligenceAPI.generateMetaDescription(context);
                    if (result && result.success) {
                        displaySingleResult(result.metaDescription, resultsDiv, 'meta');
						if (applyAfter && result.metaDescription) {
							contentToApply = result.metaDescription;
							applyType = 'text';
						}
                    } else {
                        showError(resultsDiv, (result && result.error) || 'Generation failed');
                    }
                    break;
                }
                case 'alttext':
                    result = await window.ContentIntelligenceAPI.generateAltText(context);
                    if (result.success) {
                        displaySingleResult(result.altText, resultsDiv, 'alt');
						if (applyAfter && result.altText) {
							contentToApply = result.altText;
							applyType = 'text';
						}
                    }
                    break;
            }

            if (result && !result.success) {
                showError(resultsDiv, result.error || 'Generation failed');
            } else if (applyAfter && contentToApply) {
				// Open component selector to apply the generated content
				showComponentSelector(contentToApply, applyType);
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

    // (removed) Structure handlers and helpers

    function displayHeadlines(headlines, container) {
        container.empty();
        const $resultBox = $('<div class="result-box"></div>');
        headlines.forEach(function(headline) {
            const $item = $('<div class="result-item"></div>');
            const $text = $('<div class="result-text"></div>').text(headline);
            const $meta = $('<div class="result-meta" style="font-size:12px;color:#666;margin-top:4px;"></div>').text(renderQualityMeta('headline', headline));
            const $actions = $('<div class="result-actions"></div>');
            const $copyBtn = $('<button class="btn-icon btn-copy" title="Copy">📋</button>');
            const $applyBtn = $('<button class="btn-icon btn-apply" title="Apply to Page">✨</button>').data('content', headline).data('type', 'headline');
            $actions.append($copyBtn, $applyBtn);
            $item.append($text, $meta, $actions);
            $resultBox.append($item);
        });
        container.append($resultBox);
    }

    function displaySingleResult(content, container, type = 'text') {
        container.empty();
        appendSingleResult(content, container, type);
    }

    function appendSingleResult(content, container, type = 'text') {
        const $resultBox = $('<div class="result-box"></div>');
        const $item = $('<div class="result-item"></div>');
        const $text = $('<div class="result-text"></div>').text(content);
        const $meta = $('<div class="result-meta" style="font-size:12px;color:#666;margin-top:4px;"></div>').text(renderQualityMeta(type, content));
        const $actions = $('<div class="result-actions"></div>');
        const $copyBtn = $('<button class="btn-icon btn-copy" title="Copy">📋</button>');
        const $applyBtn = $('<button class="btn-icon btn-apply" title="Apply to Page">✨</button>').data('content', content).data('type', type);
        $actions.append($copyBtn);
        $actions.append($applyBtn);
        $item.append($text, $meta, $actions);
        $resultBox.append($item);
        container.append($resultBox);
    }

    function renderQualityMeta(type, text) {
        const t = String(text || '');
        if (type === 'headline') {
            const len = t.length;
            const good = (len >= 35 && len <= 65);
            return `Length: ${len} ${good ? '✓' : len < 35 ? '(short)' : '(long)'}`;
        }
        if (type === 'meta' || type === 'alt') {
            const len = t.length;
            const good = (len >= 80 && len <= 160);
            return `Length: ${len} ${good ? '✓' : len < 80 ? '(short)' : '(long)'}`;
        }
        if (type === 'body') {
            const words = (t.trim().split(/\s+/) || []).filter(Boolean).length;
            const mins = words / 200;
            const time = mins < 1 ? `${Math.max(10, Math.round(mins*60))}s` : `${Math.round(mins)}m`;
            return `${words} words • ~${time} read`;
        }
        const len = t.length;
        return `Length: ${len}`;
    }

    function getSelectedTone() {
        const v = $('#generate-tone').val();
        return v || 'professional';
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

    function displayBrandComparison(data, container) {
        const profileInfo = data.profileId ? `Profile: ${data.profileId}` : 'Profile: (not set)';
        const html = `
            <div class="result-box" style="padding:0;">
                <div style="display:flex; gap:16px; flex-wrap:wrap;">
                    <div style="flex:1 1 300px; min-width:280px; border-right:1px solid #eee; padding:16px;">
                        <div style="font-weight:600; margin-bottom:8px;">❌ Generic AI</div>
                        <div class="result-item">
                            <div class="result-text">${escapeHtml(data.generic || '')}</div>
                        </div>
                    </div>
                    <div style="flex:1 1 300px; min-width:280px; padding:16px;">
                        <div style="font-weight:600; margin-bottom:8px;">✅ Brand Voice</div>
                        <div class="result-item">
                            <div class="result-text">${escapeHtml(data.brand || '')}</div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="result-box" style="margin-top:12px;">
                <strong style="display:block; margin-bottom:8px;">Style Profile</strong>
                <div class="result-item">
                    <div class="result-text">${escapeHtml(profileInfo)}</div>
                </div>
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
        
		// Update length slider label
		try {
			const $len = $('#generate-length');
			if ($len.length) {
				$('#generate-length-value').text($len.val());
			}
		} catch (e) {}
        
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

	async function handleVisionAnalyze() {
		const path = $('#vision-image-path').val().trim();
		const analysis = $('#vision-analysis-type').val();
		const btn = $('#vision-analyze-btn');
		const resultsDiv = $('#vision-results');
		if (!path) {
			showError(resultsDiv, 'Please enter a DAM image path');
			return;
		}
		if (!path.startsWith('/content/dam/')) {
			showError(resultsDiv, 'Please provide a valid DAM path starting with /content/dam/');
			return;
		}
		btn.addClass('loading').prop('disabled', true);
		resultsDiv.empty();
		try {
			const prompt = buildVisionPrompt(analysis);
			const result = await window.ContentIntelligenceAPI.analyzeImage(path, prompt);
			if (result && result.success) {
				displayVisionResults(result, resultsDiv);
			} else {
				const err = (result && result.error) ? result.error : 'Image analysis failed';
				showError(resultsDiv, err);
			}
		} catch (e) {
			showError(resultsDiv, e.message || 'Error analyzing image');
		} finally {
			btn.removeClass('loading').prop('disabled', false);
		}
	}

    // Initialize preset dropdown and context-aware default
	function initGeneratePreset() {
		// Update length value label live
		$(document).on('input change', '#generate-length', function() {
			$('#generate-length-value').text($(this).val());
		});

		let preset = null;
		try {
			preset = localStorage.getItem('copilot:generatePreset') || null;
		} catch (e) {}

		if (!preset) {
			preset = determinePresetFromSelection() || 'headlines';
		}
		setGeneratePreset(preset, false);
	}

	function setGeneratePreset(preset, persist = false) {
        const $sel = $('#generate-preset-select');
        if ($sel.length) {
            $sel.val(preset);
        }
		updateGenerateForm(preset);
		if (persist) {
			try { localStorage.setItem('copilot:generatePreset', preset); } catch (e) {}
		}
	}

	function determinePresetFromSelection() {
		try {
			// Granite overlay selection
			const selected = document.querySelector('.cq-Overlay.is-selected, .cq-Overlay.is-active');
			const rt = selected && (selected.getAttribute('data-resource-type') || selected.getAttribute('data-resourcetype')) || '';
			const preset = presetForResourceType(rt);
			if (preset) return preset;

			// Try Granite author API
			if (window.Granite && window.Granite.author && Array.isArray(window.Granite.author.editables)) {
				const ed = window.Granite.author.editables.find(e => e && e.selected);
				if (ed) {
					const edRt = (ed.type) || (ed.overlay && ed.overlay.dataset && (ed.overlay.dataset.resourceType || ed.overlay.dataset.resourcetype)) || '';
					const p = presetForResourceType(edRt);
					if (p) return p;
				}
			}
		} catch (e) {}
		return null;
	}

	function presetForResourceType(rt) {
		if (!rt) return null;
		const lower = rt.toLowerCase();
		if (lower.includes('/title')) return 'headlines';
		if (lower.includes('/text')) return 'bodycopy';
		if (lower.includes('/image')) return 'alttext';
		return null;
	}

	function buildVisionPrompt(type) {
		switch (type) {
			case 'seo':
				return "Describe this image for SEO: include key visual elements and keywords; then provide a concise alt text prefixed with 'ALT:'";
			case 'accessibility':
				return "Describe this image for screen readers in clear, neutral language (1-2 sentences), then a concise alt text prefixed with 'ALT:'";
			case 'objects':
				return "List key objects and scene elements you see, then provide a concise alt text prefixed with 'ALT:'";
			case 'alt':
			default:
				return "Describe this image in 1-2 sentences and then provide a concise alt text prefixed with 'ALT:'";
		}
	}

	function displayVisionResults(result, container) {
		const description = result.description || '';
		const alt = (result.altText || '').slice(0, 125);
		const tags = Array.isArray(result.tags) ? result.tags : deriveTags(description);
		const tagsStr = tags.join(', ');
		const html = `
			<div class="vision-results-box">
				<div class="vision-image-preview">Image Preview (from DAM)</div>
			</div>
			<div class="result-box">
				<strong style="display:block;margin-bottom:8px;">Full Description</strong>
				<div class="result-item">
					<div class="result-text">${escapeHtml(description)}</div>
					<div class="result-actions"><button class="btn-icon btn-copy" title="Copy">📋</button></div>
				</div>
			</div>
			<div class="result-box" style="margin-top:12px;">
				<strong style="display:block;margin-bottom:8px;">Alt Text (≤125 chars)</strong>
				<div class="result-item">
					<div class="result-text">${escapeHtml(alt)}</div>
					<div class="result-actions"><button class="btn-icon btn-copy" title="Copy">📋</button></div>
				</div>
			</div>
			<div class="result-box" style="margin-top:12px;">
				<strong style="display:block;margin-bottom:8px;">Suggested Tags</strong>
				<div class="result-item">
					<div class="result-text">
						<div class="vision-tags">${tags.map(t => `<span class=\"tag-pill\">${escapeHtml(t)}</span>`).join(' ')}</div>
					</div>
					<div class="result-actions"><button class="btn-icon btn-copy" title="Copy">📋</button></div>
				</div>
			</div>
		`;
		container.html(html);
	}

	function deriveTags(text) {
		if (!text) return [];
		const stop = new Set(['the','and','with','for','that','this','from','into','over','under','near','on','in','at','to','a','an','of','by','is','are','it','as','be']);
		const words = text.toLowerCase().replace(/[^a-z0-9\s]/g,'').split(/\s+/).filter(w => w && !stop.has(w) && w.length > 3);
		const freq = {};
		words.forEach(w => freq[w] = (freq[w] || 0) + 1);
		return Object.entries(freq)
			.sort((a,b) => b[1]-a[1])
			.slice(0, 10)
			.map(([w]) => w);
	}

})(window.jQuery || window.Granite.$, document);

