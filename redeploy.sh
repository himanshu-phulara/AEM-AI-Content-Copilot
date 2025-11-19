#!/bin/bash
set -e

echo "════════════════════════════════════════════════════"
echo "  🔥 FORCE REDEPLOY - AEM Content Intelligence"
echo "════════════════════════════════════════════════════"
echo ""

cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot

echo "1️⃣  Building package..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    
    echo "2️⃣  Deploying to AEM..."
    curl -s -u admin:admin \
        -F file=@"all/target/aem-ai-content-copilot.all-1.0.0.zip" \
        -F name="aem-ai-content-copilot-fix" \
        -F force=true \
        -F install=true \
        http://localhost:4502/crx/packmgr/service.jsp | grep -o "Package installed"
    echo ""
    
    echo "3️⃣  Rebuilding clientlibs cache..."
    curl -s -u admin:admin -X POST \
        http://localhost:4502/libs/granite/ui/content/dumplibs.rebuild.html > /dev/null
    echo "✅ Clientlibs rebuilt"
    echo ""
    
    echo "4️⃣  Invalidating resolver cache..."
    curl -s -u admin:admin -X POST \
        "http://localhost:4502/system/console/jcrresolver" \
        -d "action=refresh" > /dev/null
    echo "✅ Cache invalidated"
    echo ""
    
    echo "5️⃣  Verifying deployment..."
    if curl -s -u admin:admin "http://localhost:4502/apps/intelligence/clientlibs/copilot/js/copilot.js" | grep -q "🔍"; then
        echo "✅ NEW CODE DETECTED!"
    else
        echo "⚠️  May still be cached, try incognito"
    fi
    echo ""
    
    echo "════════════════════════════════════════════════════"
    echo "  ✅ DEPLOYMENT COMPLETE!"
    echo "════════════════════════════════════════════════════"
    echo ""
    echo "📋 Next Steps:"
    echo "  1. CLOSE your browser tab completely"
    echo "  2. Open NEW INCOGNITO/PRIVATE window"
    echo "  3. Go to: http://localhost:4502/editor.html/content/wknd/language-masters/en/magazine/test.html"
    echo "  4. Look for: 🔍 emoji in console"
    echo ""
else
    echo "❌ Build failed!"
    exit 1
fi

