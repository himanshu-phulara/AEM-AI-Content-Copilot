#!/bin/bash

# AEM AI Content Copilot - Installation Verification Script
# Tests all components to ensure everything is working

echo "🔍 AEM AI Content Copilot - Installation Test"
echo "======================================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

# Test 1: Check if Ollama is running
echo "Test 1: Checking Ollama service..."
if curl -s http://localhost:11434 > /dev/null 2>&1; then
    echo -e "${GREEN}✅ PASSED${NC} - Ollama is running"
    ((PASSED++))
else
    echo -e "${RED}❌ FAILED${NC} - Ollama is not running"
    echo "   Run: ollama serve"
    ((FAILED++))
fi
echo ""

# Test 2: Check if Ollama model is available
echo "Test 2: Checking Ollama model..."
if ollama list 2>/dev/null | grep -q "llama3.2"; then
    echo -e "${GREEN}✅ PASSED${NC} - Llama3.2 model is available"
    ((PASSED++))
else
    echo -e "${RED}❌ FAILED${NC} - Llama3.2 model not found"
    echo "   Run: ollama pull llama3.2:3b"
    ((FAILED++))
fi
echo ""

# Test 3: Check if AEM is running
echo "Test 3: Checking AEM instance..."
if curl -s -u admin:admin http://localhost:4502/system/console/bundles.json > /dev/null 2>&1; then
    echo -e "${GREEN}✅ PASSED${NC} - AEM is running on localhost:4502"
    ((PASSED++))
else
    echo -e "${RED}❌ FAILED${NC} - AEM is not accessible"
    echo "   Ensure AEM is running on localhost:4502"
    ((FAILED++))
fi
echo ""

# Test 4: Check if Intelligence bundle is installed
echo "Test 4: Checking Intelligence bundle..."
BUNDLE_CHECK=$(curl -s -u admin:admin http://localhost:4502/system/console/bundles.json | grep -i "intelligence")
if [ ! -z "$BUNDLE_CHECK" ]; then
    echo -e "${GREEN}✅ PASSED${NC} - Intelligence bundle is installed"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠️  WARNING${NC} - Intelligence bundle not found"
    echo "   The package may not be installed yet"
    ((FAILED++))
fi
echo ""

# Test 5: Check health endpoint
echo "Test 5: Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -u admin:admin http://localhost:4502/bin/intelligence/health)
if echo "$HEALTH_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ PASSED${NC} - Health endpoint is working"
    if echo "$HEALTH_RESPONSE" | grep -q '"ollamaAvailable":true'; then
        echo "   Status: AI services are healthy"
    else
        echo -e "   ${YELLOW}Warning: Ollama integration not available${NC}"
    fi
    ((PASSED++))
else
    echo -e "${YELLOW}⚠️  WARNING${NC} - Health endpoint not responding"
    echo "   The package may not be fully deployed"
    ((FAILED++))
fi
echo ""

# Test 6: Test content generation
echo "Test 6: Testing content generation..."
GENERATE_RESPONSE=$(curl -s -u admin:admin -X POST http://localhost:4502/bin/intelligence/generate \
    -d "action=headlines" \
    -d "context=test content" \
    -d "count=1")

if echo "$GENERATE_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ PASSED${NC} - Content generation is working"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠️  WARNING${NC} - Content generation not working"
    echo "   Response: $GENERATE_RESPONSE"
    ((FAILED++))
fi
echo ""

# Test 7: Check if clientlib is deployed
echo "Test 7: Checking clientlib deployment..."
CLIENTLIB_CHECK=$(curl -s -u admin:admin http://localhost:4502/apps/intelligence/clientlibs/copilot)
if echo "$CLIENTLIB_CHECK" | grep -q "copilot"; then
    echo -e "${GREEN}✅ PASSED${NC} - Clientlib is deployed"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠️  WARNING${NC} - Clientlib may not be deployed"
    ((FAILED++))
fi
echo ""

# Summary
echo "======================================================"
echo "Test Summary:"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed! Your installation is ready!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Open: http://localhost:4502/editor.html/content/wknd/us/en.html"
    echo "2. Look for the purple ✨ button (bottom-right)"
    echo "3. Click it and start generating content!"
    echo ""
    echo "For demo preparation, see: DEMO_SCRIPT.md"
else
    echo -e "${YELLOW}⚠️  Some tests failed. Please check the issues above.${NC}"
    echo ""
    echo "Common fixes:"
    echo "1. Start Ollama: ollama serve"
    echo "2. Pull model: ollama pull llama3.2:3b"
    echo "3. Deploy package via Package Manager"
    echo ""
    echo "For detailed help, see: INSTALLATION.md"
fi

echo ""
echo "======================================================"

