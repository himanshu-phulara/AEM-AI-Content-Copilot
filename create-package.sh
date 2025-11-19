#!/bin/bash

# AEM Content Intelligence Suite - Manual Package Creator
# Use this if Maven is not available

echo "🚀 Creating AEM Content Intelligence package..."
echo ""

# Set paths
PROJECT_DIR="/Users/hphulara/Documents/AEM/aem-ai-content-copilot"
BUILD_DIR="$PROJECT_DIR/manual-build"
PACKAGE_DIR="$BUILD_DIR/package"

# Clean previous build
echo "📦 Cleaning previous build..."
rm -rf "$BUILD_DIR"
mkdir -p "$PACKAGE_DIR/jcr_root/apps/intelligence/install"
mkdir -p "$PACKAGE_DIR/META-INF/vault"

# Copy Java source as-is (AEM will compile OSGi bundle)
echo "📋 Copying core bundle sources..."
cp -r "$PROJECT_DIR/core/src/main/java" "$PACKAGE_DIR/jcr_root/apps/intelligence/install/"

# Copy manifest for core bundle
cat > "$PACKAGE_DIR/jcr_root/apps/intelligence/install/.content.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0"
    jcr:primaryType="sling:Folder"/>
EOF

# Copy UI apps
echo "📋 Copying UI components..."
if [ -d "$PROJECT_DIR/ui.apps/src/main/content/jcr_root/apps/intelligence" ]; then
    cp -r "$PROJECT_DIR/ui.apps/src/main/content/jcr_root/apps/intelligence"/* "$PACKAGE_DIR/jcr_root/apps/intelligence/"
fi

# Create vault metadata
echo "📋 Creating package metadata..."

cat > "$PACKAGE_DIR/META-INF/vault/filter.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<workspaceFilter version="1.0">
    <filter root="/apps/intelligence" mode="merge"/>
</workspaceFilter>
EOF

cat > "$PACKAGE_DIR/META-INF/vault/properties.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <entry key="name">AEM Content Intelligence Suite</entry>
    <entry key="description">AI-powered content intelligence for AEM Sites</entry>
    <entry key="group">com.adobe.aem.intelligence</entry>
    <entry key="version">1.0.0-manual</entry>
    <entry key="createdBy">Garage Week 2025</entry>
</properties>
EOF

cat > "$PACKAGE_DIR/META-INF/vault/config.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<vaultfs version="1.1">
    <aggregate>
        <include>.*</include>
    </aggregate>
</vaultfs>
EOF

# Create package info
cat > "$PACKAGE_DIR/META-INF/vault/definition/.content.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:vlt="http://www.day.com/jcr/vault/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
    jcr:created="{Date}2024-11-14T00:00:00.000-08:00"
    jcr:createdBy="admin"
    jcr:description="AI-powered content intelligence for AEM Sites - Garage Week Winter 2025"
    jcr:lastModified="{Date}2024-11-14T00:00:00.000-08:00"
    jcr:lastModifiedBy="admin"
    jcr:primaryType="vlt:PackageDefinition"
    group="com.adobe.aem.intelligence"
    name="aem-ai-content-copilot"
    version="1.0.0">
    <filter jcr:primaryType="nt:unstructured">
        <f0
            jcr:primaryType="nt:unstructured"
            mode="merge"
            root="/apps/intelligence"
            rules="[]"/>
    </filter>
</jcr:root>
EOF

# Create the ZIP
echo "🗜️  Creating ZIP package..."
cd "$PACKAGE_DIR"
zip -r "$BUILD_DIR/aem-ai-content-copilot-manual.zip" . -x "*.DS_Store"
cd "$PROJECT_DIR"

# Success!
echo ""
echo "✅ Package created successfully!"
echo ""
echo "📦 Package location:"
echo "   $BUILD_DIR/aem-ai-content-copilot-manual.zip"
echo ""
echo "📥 To install:"
echo "   1. Open: http://localhost:4502/crx/packmgr"
echo "   2. Upload this ZIP file"
echo "   3. Click Install"
echo ""
echo "🎉 Ready for Garage Week demo!"

