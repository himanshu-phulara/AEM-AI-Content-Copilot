package com.adobe.aem.intelligence.servlets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo reset endpoint: clears cached patterns and brand voice artifacts for a fresh demo state.
 * Protected: requires admin user.
 */
@Component(
	service = Servlet.class,
	property = {
		"sling.servlet.paths=/bin/intelligence/demo/reset",
		"sling.servlet.methods=[POST]"
	}
)
public class ResetDemoServlet extends SlingAllMethodsServlet {

	private static final Logger LOG = LoggerFactory.getLogger(ResetDemoServlet.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Reference
	private SlingSettingsService slingSettings;

	@Override
	protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonObject out = new JsonObject();
		List<String> removed = new ArrayList<>();
		long start = System.currentTimeMillis();

		try {
			// Simple admin check (demo-purpose): require AEM admin user
			String userId = request.getResourceResolver().getUserID();
			if (!"admin".equals(userId)) {
				response.setStatus(SlingHttpServletResponse.SC_FORBIDDEN);
				out.addProperty("success", false);
				out.addProperty("error", "Forbidden: admin user required");
				response.getWriter().write(GSON.toJson(out));
				return;
			}

			Path baseDir = getCacheDir();
			if (baseDir == null) {
				out.addProperty("success", false);
				out.addProperty("error", "Could not resolve cache directory");
				response.getWriter().write(GSON.toJson(out));
				return;
			}

			// Files to remove
			String[] targets = new String[] { "patterns.json", "page_summaries.json", "brand_profile.json" };
			for (String f : targets) {
				Path p = baseDir.resolve(f);
				try {
					Files.deleteIfExists(p);
					removed.add(p.toAbsolutePath().toString());
				} catch (Exception ex) {
					LOG.warn("Failed to remove {}: {}", p, ex.getMessage());
				}
			}

			// If directory is empty after deletions, attempt to remove it (best-effort)
			try {
				File dir = baseDir.toFile();
				File[] left = dir.listFiles();
				if (left != null && left.length == 0) {
					Files.deleteIfExists(baseDir);
				}
			} catch (Exception ignore) {}

			out.addProperty("success", true);
			out.add("removed", GSON.toJsonTree(removed));
			out.addProperty("durationMs", System.currentTimeMillis() - start);
			out.addProperty("message", "Demo state reset. Run warmup again before presenting.");
		} catch (Exception e) {
			LOG.error("Demo reset failed", e);
			out.addProperty("success", false);
			out.addProperty("error", e.getMessage());
		} finally {
			response.getWriter().write(GSON.toJson(out));
		}
	}

	private Path getCacheDir() {
		try {
			String home = slingSettings != null ? slingSettings.getSlingHomePath() : null;
			File base = StringUtils.isNotBlank(home) ? new File(home) : new File(System.getProperty("java.io.tmpdir"));
			return new File(base, "var/aem-ai-content-copilot").toPath();
		} catch (Exception e) {
			return null;
		}
	}
}
