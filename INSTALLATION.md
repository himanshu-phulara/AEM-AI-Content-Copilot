# Installation Guide - AEM AI Content Copilot

## Prerequisites

### 1. AEM Instance
- AEM 6.5+ or AEM as a Cloud Service
- Running on `localhost:4502`
- Admin credentials: `admin/admin`

### 2. Ollama Setup
Install and configure Ollama for local AI:

```bash
# Install Ollama (macOS)
brew install ollama

# Or download from: https://ollama.ai

# Pull the Llama model
ollama pull llama3.2:3b

# Start Ollama service
ollama serve
```

Verify Ollama is running:
```bash
curl http://localhost:11434
# Should return: "Ollama is running"
```

### 3. Build Tools (Optional - for building from source)
- Java 11+
- Maven 3.6+

---

## Installation Methods

### Method 1: Quick Install (No Build Required) ⚡

**If you have pre-built packages:**

1. **Open AEM Package Manager**
   ```
   http://localhost:4502/crx/packmgr/index.jsp
   ```

2. **Upload the package**
   - Click "Upload Package"
   - Select `aem-ai-content-copilot.all-1.0.0.zip`
   - Click "OK"

3. **Install the package**
   - Find "AEM AI Content Copilot" in the list
   - Click "Install"
   - Wait for installation to complete (30-60 seconds)

4. **Verify Installation**
   - Go to: http://localhost:4502/system/console/bundles
   - Search for "intelligence"
   - Bundle should be "Active" ✅

---

### Method 2: Build and Deploy from Source

#### Step 1: Build the Project

```bash
cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot

# Build all modules
mvn clean install

# Or build and deploy directly to AEM
mvn clean install -PautoInstallPackage
```

#### Step 2: Deploy to AEM

**Option A: Using Maven**
```bash
mvn clean install -PautoInstallPackage
```

**Option B: Using curl**
```bash
cd all/target
curl -u admin:admin -F file=@"aem-ai-content-copilot.all-1.0.0.zip" \
  -F name="aem-ai-content-copilot" \
  -F force=true \
  -F install=true \
  http://localhost:4502/crx/packmgr/service/.json
```

**Option C: Manual Upload**
1. Navigate to Package Manager: http://localhost:4502/crx/packmgr
2. Upload `all/target/aem-ai-content-copilot.all-1.0.0.zip`
3. Click Install

---

## Post-Installation Setup

### 1. Configure Ollama Connection (Optional)

The default configuration should work, but you can customize:

1. Go to OSGi Configuration Console:
   ```
   http://localhost:4502/system/console/configMgr
   ```

2. Find "AEM Content Intelligence - Ollama Configuration"

3. Adjust settings if needed:
   - **Ollama API URL**: `http://localhost:11434` (default)
   - **Default Model**: `llama3.2:3b` (default)
   - **Temperature**: `0.7` (default)

4. Click "Save"

### 2. Verify Services are Running

**Check Service Health:**
```bash
curl http://localhost:4502/bin/intelligence/health
```

Expected response:
```json
{
  "success": true,
  "ollamaAvailable": true,
  "status": "healthy",
  "message": "AI Content Intelligence is ready"
}
```

### 3. Test the Installation

1. Open any WKND page in edit mode:
   ```
   http://localhost:4502/editor.html/content/wknd/us/en/magazine/guide-la-skateparks.html
   ```

2. Look for the **purple floating AI button** (bottom-right corner)

3. Click it to open the AI Co-Pilot panel

4. Try generating a headline:
   - Tab: "Generate"
   - Select: "Headlines"
   - Context: "Skateboarding parks in Los Angeles"
   - Click: "✨ Generate Content"

5. You should see 3 AI-generated headlines! 🎉

---

## Troubleshooting

### Issue: "Ollama Offline" warning

**Solution:**
```bash
# Check if Ollama is running
ps aux | grep ollama

# If not running, start it:
ollama serve

# In another terminal, verify:
curl http://localhost:11434
```

### Issue: Bundle not active

**Solution:**
1. Go to: http://localhost:4502/system/console/bundles
2. Find "AEM Content Intelligence Core"
3. Click "Start" if it's stopped
4. Check logs: http://localhost:4502/system/console/slinglog

### Issue: AI button not appearing

**Solution:**
1. Hard refresh the page: `Cmd+Shift+R` (Mac) or `Ctrl+Shift+R` (Windows)
2. Check browser console for errors
3. Verify clientlib is loaded:
   ```
   View Page Source → Search for "intelligence"
   ```

### Issue: Generation fails

**Check these:**
1. Ollama is running: `curl http://localhost:11434`
2. Model is available: `ollama list` (should show llama3.2:3b)
3. Check AEM logs: http://localhost:4502/system/console/slinglog
4. Test API directly:
   ```bash
   curl -X POST http://localhost:4502/bin/intelligence/health
   ```

---

## Uninstallation

To remove the package:

1. Go to Package Manager: http://localhost:4502/crx/packmgr
2. Find "AEM AI Content Copilot"
3. Click "Uninstall"
4. Click "Delete" to remove completely

---

## Next Steps

✅ Installation complete!  
✅ Services are healthy  
✅ AI Co-Pilot is ready  

Now check out:
- **[DEMO_SCRIPT.md](./DEMO_SCRIPT.md)** - How to present this for Garage Week
- **[README.md](./README.md)** - Feature overview
- **[PRESENTATION.md](./PRESENTATION.md)** - Pitch deck content

---

## Support

For issues or questions:
- Check logs: http://localhost:4502/system/console/slinglog
- Verify health: http://localhost:4502/bin/intelligence/health
- Review documentation: README.md

Built for Adobe AEM Garage Week Winter 2025 🚀

