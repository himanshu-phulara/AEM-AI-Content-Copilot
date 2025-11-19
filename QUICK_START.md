# Quick Start Guide - Get Running in 5 Minutes

## Fastest Path to Demo-Ready

### Step 1: Start Ollama (2 minutes)

```bash
# If not installed yet:
brew install ollama

# Pull the model (one-time, ~2GB download)
ollama pull llama3.2:3b

# Start Ollama service
ollama serve
```

Keep this terminal open! Ollama must be running.

**Verify it's working:**
```bash
# In a new terminal:
curl http://localhost:11434
# Should return: "Ollama is running"
```

---

### Step 2: Create Manual Package (if Maven fails)

Since you may not have Maven configured, here's how to create a deployable package manually:

#### Option A: Use the Build Script

```bash
cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot
./create-package.sh
```

This will create: `aem-ai-content-copilot-manual.zip`

#### Option B: Manual ZIP Creation

```bash
cd /Users/hphulara/Documents/AEM/aem-ai-content-copilot

# Create package structure
mkdir -p package/jcr_root/apps/intelligence
mkdir -p package/META-INF/vault

# Copy files
cp -r core/src/main/java package/jcr_root/apps/intelligence/
cp -r ui.apps/src/main/content/jcr_root/apps/intelligence/* package/jcr_root/apps/intelligence/
cp ui.apps/src/main/content/META-INF/vault/* package/META-INF/vault/

# Create ZIP
cd package
zip -r ../aem-ai-content-copilot-manual.zip .
cd ..
```

---

### Step 3: Deploy to AEM (1 minute)

1. **Open Package Manager:**
   ```
   http://localhost:4502/crx/packmgr/index.jsp
   ```
   Login: `admin/admin`

2. **Upload & Install:**
   - Click "Upload Package"
   - Choose your ZIP file
   - Click "OK"
   - Click "Install"

3. **Wait for Success Message** (30-60 seconds)

---

### Step 4: Verify (30 seconds)

**Check Services:**
```bash
curl http://localhost:4502/bin/intelligence/health
```

Should return:
```json
{
  "success": true,
  "ollamaAvailable": true,
  "status": "healthy"
}
```

---

### Step 5: Test It! (1 minute)

1. **Open a WKND page in edit mode:**
   ```
   http://localhost:4502/editor.html/content/wknd/us/en.html
   ```

2. **Look for the purple ✨ button** (bottom-right)

3. **Click it!** The AI Co-Pilot panel opens

4. **Try it:**
   - Tab: "Generate"
   - Type: "Headlines"
   - Context: "Adventure travel in California"
   - Click: "✨ Generate Content"

5. **See the magic happen!** 🎉

---

## Troubleshooting

### "Ollama Offline" Warning

```bash
# Check if running:
ps aux | grep ollama

# Restart:
pkill ollama
ollama serve
```

### AI Button Not Appearing

```bash
# Hard refresh browser:
# Mac: Cmd + Shift + R
# Windows: Ctrl + Shift + R
```

### Generation Fails

```bash
# Test Ollama directly:
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2:3b",
  "prompt": "Say hello",
  "stream": false
}'

# Should return JSON with response
```

---

## Ready for Demo!

✅ Ollama running  
✅ Package deployed  
✅ Services healthy  
✅ AI Co-Pilot working  

**Next steps:**
- Practice with [DEMO_SCRIPT.md](./DEMO_SCRIPT.md)
- Review features in [README.md](./README.md)
- Prepare your pitch!

---

## Alternative: Use Pre-Built Package

If you were given a pre-built `.zip` file, just skip to Step 3 and deploy that directly!

---

_You're 5 minutes away from winning Garage Week! 🚀_

