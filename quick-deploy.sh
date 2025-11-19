#!/bin/bash
cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot
echo "Building..."
mvn clean package -DskipTests -q
echo "Deploying..."
curl -s -u admin:admin -F file=@"all/target/aem-ai-content-copilot.all-1.0.0.zip" -F name="aem-ci" -F force=true -F install=true http://localhost:4502/crx/packmgr/service.jsp | grep -o "installed"
curl -s -u admin:admin -X POST http://localhost:4502/libs/granite/ui/content/dumplibs.rebuild.html > /dev/null
echo "✅ Done! Close browser tab and open NEW incognito window"

