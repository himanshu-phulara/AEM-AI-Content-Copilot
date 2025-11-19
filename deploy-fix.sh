#!/bin/bash
# Deploy script for ContentFrame fix

cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot

echo "═══════════════════════════════════════════"
echo "  AEM Content Intelligence - DEPLOY"
echo "═══════════════════════════════════════════"
echo ""
echo "🔨 Building package..."
mvn clean package -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    echo "🚀 Deploying to AEM localhost:4502..."
    RESULT=$(curl -s -u admin:admin -F file=@"all/target/aem-ai-content-copilot.all-1.0.0.zip" -F name="aem-ai-content-copilot" -F force=true -F install=true http://localhost:4502/crx/packmgr/service.jsp)
    
    if echo "$RESULT" | grep -q "Package installed"; then
        echo "✅ Package deployed successfully!"
    else
        echo "⚠️  Deployment may have issues, check AEM"
    fi
    
    echo ""
    echo "═══════════════════════════════════════════"
    echo "  🎉 DEPLOYMENT COMPLETE!"
    echo "═══════════════════════════════════════════"
    echo ""
    echo "📋 Next steps:"
    echo "  1. Refresh browser (Cmd+R or Ctrl+R)"
    echo "  2. Open Browser Console (F12)"
    echo "  3. Click Apply button"
    echo "  4. Look for: 🔍 ✅ emoji in logs"
    echo ""
else
    echo "❌ Build failed! Check errors above."
    exit 1
fi

