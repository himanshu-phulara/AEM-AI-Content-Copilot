# Contributing to AEM AI Content Copilot

First off, thank you for considering contributing to the AEM AI Content Copilot! 🎉

This document provides guidelines and instructions for contributing to this project.

## 🤝 How Can I Contribute?

### Reporting Bugs 🐛

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include as many details as possible:

**Bug Report Template:**
```markdown
**Description**
A clear and concise description of the bug.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '...'
3. Scroll down to '...'
4. See error

**Expected Behavior**
What you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Environment**
- AEM Version: [e.g. 6.5.15]
- Java Version: [e.g. 11.0.18]
- Ollama Version: [e.g. 0.1.17]
- Browser: [e.g. Chrome 120]
- OS: [e.g. macOS 13.5]

**Additional Context**
Add any other context about the problem here.
```

### Suggesting Enhancements 💡

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

**Enhancement Template:**
```markdown
**Is your feature request related to a problem?**
A clear description of the problem.

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Alternative solutions or features you've considered.

**Additional context**
Any other context, mockups, or screenshots about the feature request.
```

### Pull Requests 🚀

1. **Fork the repository** and create your branch from `main`
2. **Make your changes** following the coding standards below
3. **Write tests** if applicable
4. **Update documentation** if you change functionality
5. **Ensure the build passes**: `mvn clean install`
6. **Create a Pull Request** with a clear description

## 🛠️ Development Setup

### Prerequisites

- Java 11+
- Maven 3.6+
- AEM 6.5+ (local instance)
- Ollama with Llama 3.2:3b
- Git

### Setup Instructions

```bash
# 1. Fork and clone the repository
git clone https://github.com/YOUR_USERNAME/AEM-AI-Content-Copilot.git
cd AEM-AI-Content-Copilot

# 2. Build the project
mvn clean install

# 3. Deploy to local AEM (if configured)
mvn clean install -PautoInstallPackage

# 4. Start Ollama
ollama serve

# 5. Test your changes
curl http://localhost:4502/bin/intelligence/health
```

## 📝 Coding Standards

### Java

- **Style**: Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **Formatting**: Use 4 spaces for indentation
- **Naming**: Use camelCase for methods and variables, PascalCase for classes
- **Documentation**: Add Javadoc for all public methods
- **Logging**: Use SLF4J, appropriate log levels

**Example:**
```java
/**
 * Generates AI-powered content based on the provided prompt.
 *
 * @param prompt The content generation prompt
 * @param model The AI model to use
 * @param temperature The creativity temperature (0.0 - 1.0)
 * @return Generated content as a string
 */
@Override
public String generateContent(String prompt, String model, double temperature) {
    LOG.debug("Generating content with model: {}", model);
    // Implementation
}
```

### JavaScript

- **Style**: Use ES6+ syntax where possible
- **Formatting**: Use 4 spaces for indentation
- **Naming**: Use camelCase for functions and variables
- **Comments**: Add JSDoc for complex functions
- **jQuery**: Use `$` prefix for jQuery objects

**Example:**
```javascript
/**
 * Extracts page context from the AEM editor
 * @returns {Object} Page context including title, content, components
 */
function extractPageContext() {
    const context = {
        title: getPageTitle(),
        content: getPageContent(),
        componentCount: getComponentCount()
    };
    return context;
}
```

### CSS

- **Style**: Use BEM-like naming convention
- **Formatting**: Use 4 spaces for indentation
- **Organization**: Group related styles together
- **Comments**: Add section comments for major UI areas

**Example:**
```css
/* AI Co-Pilot Panel */
.copilot-panel {
    position: fixed;
    right: 0;
    top: 0;
    width: 400px;
    height: 100vh;
}

.copilot-panel__header {
    padding: 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
```

## 🧪 Testing Guidelines

### Manual Testing

Before submitting a PR, test:
- [ ] All 5 tabs (Generate, SEO, Variations, Suggest, Predict)
- [ ] Content insertion into components
- [ ] Page context extraction
- [ ] Error handling (when Ollama is offline)
- [ ] Browser console for JavaScript errors
- [ ] Different AEM page templates

### Integration Testing

Test with:
- AEM WKND site pages
- Different Core Components (Title, Text, Teaser)
- Various content lengths and formats
- Different browsers (Chrome, Firefox, Safari)

## 📦 Commit Messages

Use clear and meaningful commit messages following [Conventional Commits](https://www.conventionalcommits.org/):

**Format:**
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```bash
feat(ui): add one-click content insertion to components

- Implemented component selector modal
- Added content application logic for Core Components
- Enhanced UI with animations and feedback

Closes #42

---

fix(api): handle CSRF token errors in AEM editor

The API was failing with 409 errors due to CSRF validation.
Changed to use GET requests with query parameters.

Fixes #38

---

docs(readme): update installation instructions

Added troubleshooting section for common Ollama issues.
```

## 🌿 Branching Strategy

- `main` - Production-ready code
- `develop` - Integration branch for features
- `feature/*` - New features
- `fix/*` - Bug fixes
- `docs/*` - Documentation updates

**Create a feature branch:**
```bash
git checkout -b feature/add-multi-language-support
```

## 🔄 Pull Request Process

1. **Update Documentation**: If you change functionality, update the relevant docs
2. **Update CHANGELOG**: Add your changes to CHANGELOG.md (if it exists)
3. **Self-Review**: Review your own PR first for obvious issues
4. **Link Issues**: Reference related issues in the PR description
5. **Request Review**: Tag relevant maintainers for review
6. **Address Feedback**: Respond to review comments promptly
7. **Squash Commits**: Squash minor commits before merging (if requested)

**PR Template:**
```markdown
## Description
Brief description of what this PR does.

## Related Issues
Fixes #123
Related to #456

## Changes Made
- Feature 1
- Feature 2
- Bug fix for X

## Testing
- [ ] Tested on AEM 6.5
- [ ] Tested with WKND site
- [ ] Manual testing completed
- [ ] No console errors

## Screenshots
(If applicable)

## Checklist
- [ ] Code follows project style guidelines
- [ ] Documentation updated
- [ ] Build passes (`mvn clean install`)
- [ ] Tested locally
- [ ] No breaking changes
```

## 🏗️ Project Architecture

Understanding the architecture will help you contribute effectively:

```
Frontend (UI) → API Layer → OSGi Services → AI Provider
     ↓              ↓              ↓              ↓
copilot.js → api.js → Servlets → Services → Ollama
```

**Key Components:**
1. **Frontend**: `copilot.js`, `api.js`, `copilot.css`
2. **API Layer**: `GenerateContentServlet.java`, `HealthCheckServlet.java`
3. **Services**: `ContentIntelligenceServiceImpl.java`, `OllamaServiceImpl.java`
4. **AI Integration**: Ollama HTTP API

## 🎯 Areas for Contribution

Looking for ideas? Here are areas that need work:

### High Priority 🔥
- [ ] Integration tests with JUnit
- [ ] Error handling improvements
- [ ] Performance optimization for large content
- [ ] Support for more Core Components

### Medium Priority ⚡
- [ ] Adobe Analytics integration
- [ ] Content history and versioning
- [ ] Batch content generation
- [ ] Custom AI model support (Azure OpenAI, etc.)

### Nice to Have ✨
- [ ] Multi-language support
- [ ] A/B testing recommendations
- [ ] Adobe Firefly image generation integration
- [ ] Content templates and presets

## 📚 Resources

- [AEM Documentation](https://experienceleague.adobe.com/docs/experience-manager-65.html)
- [AEM Core Components](https://experienceleague.adobe.com/docs/experience-manager-core-components/using/introduction.html)
- [Ollama Documentation](https://ollama.ai/docs)
- [OSGi Service Development](https://docs.osgi.org/specification/)

## 💬 Communication

- **Issues**: Use GitHub Issues for bug reports and feature requests
- **Discussions**: Use GitHub Discussions for questions and ideas
- **Email**: For security issues, email directly (not public)

## 🏆 Recognition

Contributors will be:
- Listed in CONTRIBUTORS.md
- Mentioned in release notes
- Acknowledged in the README

## ❓ Questions?

Don't hesitate to ask! Open an issue with the `question` label or start a discussion.

## 📜 Code of Conduct

Be respectful, inclusive, and professional. We're all here to build something great together.

---

Thank you for contributing to AEM AI Content Copilot! 🚀

*Happy Coding!* 💻✨

