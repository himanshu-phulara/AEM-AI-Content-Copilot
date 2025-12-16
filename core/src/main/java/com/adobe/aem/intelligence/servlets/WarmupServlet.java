package com.adobe.aem.intelligence.servlets;

import com.adobe.aem.intelligence.services.BrandVoiceService;
import com.adobe.aem.intelligence.services.PagePatternAnalyzer;
import com.adobe.aem.intelligence.services.model.PagePatternSummary;
import com.adobe.aem.intelligence.services.model.StyleProfile;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-triggered warmup endpoint to precompute patterns and train brand voice
 * for a smooth demo experience. Intended to be run once prior to demo.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/intelligence/warmup",
        "sling.servlet.methods=GET"
    }
)
public class WarmupServlet extends SlingAllMethodsServlet {

	private static final Logger LOG = LoggerFactory.getLogger(WarmupServlet.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Reference
	private PagePatternAnalyzer pagePatternAnalyzer;

	@Reference
	private BrandVoiceService brandVoiceService;

	@Reference
	private SlingSettingsService slingSettings;

	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		long start = System.currentTimeMillis();
		boolean force = Boolean.parseBoolean(StringUtils.defaultString(request.getParameter("force"), "false"));

		JsonObject out = new JsonObject();
		List<String> steps = new ArrayList<>();
		Map<String, Object> timings = new HashMap<>();

		try {
			Path outputDir = ensureOutputDir();
			out.addProperty("outputDir", outputDir.toAbsolutePath().toString());

			// 1) Precompute WKND patterns
			Path patternsFile = outputDir.resolve("patterns.json");
			boolean patternsSkipped = Files.exists(patternsFile) && !force;
			long t1 = System.currentTimeMillis();
			if (!patternsSkipped) {
				Map<String, Long> patterns = pagePatternAnalyzer.computeCommonPatterns("/content/wknd/us", "default", 200);
				writeJson(patternsFile, patterns);
				steps.add("patterns");
				LOG.info("Warmup: computed & cached WKND patterns -> {}", patternsFile);
			} else {
				steps.add("patterns-skipped");
				LOG.info("Warmup: patterns.json exists; skipped (use force=true to rebuild)");
			}
			timings.put("patternsMs", System.currentTimeMillis() - t1);

			// 2) Page structure summaries (selected WKND pages)
			Path summariesFile = outputDir.resolve("page_summaries.json");
			boolean summariesSkipped = Files.exists(summariesFile) && !force;
			long t2 = System.currentTimeMillis();
			if (!summariesSkipped) {
				String[] pages = new String[] {
					"/content/wknd/us/en/magazine/guide-la-skateparks",
					"/content/wknd/us/en/magazine/western-australia",
					"/content/wknd/us/en/adventures/ski-touring-mont-blanc",
					"/content/wknd/us/en/adventures/camping-australia"
				};
				List<PagePatternSummary> summaries = new ArrayList<>();
				for (String p : pages) {
					try {
						PagePatternSummary s = pagePatternAnalyzer.analyzeStructure(p, "default", true);
						summaries.add(s);
					} catch (Exception ex) {
						LOG.warn("Warmup: analyzeStructure failed for {}: {}", p, ex.getMessage());
					}
				}
				writeJson(summariesFile, summaries);
				steps.add("summaries");
				LOG.info("Warmup: generated page summaries -> {}", summariesFile);
			} else {
				steps.add("summaries-skipped");
				LOG.info("Warmup: page_summaries.json exists; skipped (use force=true to rebuild)");
			}
			timings.put("summariesMs", System.currentTimeMillis() - t2);

			// 3) Train brand voice on WKND content and save profile
			Path profileFile = outputDir.resolve("brand_profile.json");
			boolean profileSkipped = Files.exists(profileFile) && !force;
			long t3 = System.currentTimeMillis();
			String profileId = null;
			if (!profileSkipped) {
				try {
					profileId = brandVoiceService.trainProfile("/content/wknd/us");
				} catch (Exception ex) {
					LOG.warn("Warmup: trainProfile failed: {}", ex.getMessage());
				}
				if (StringUtils.isNotBlank(profileId)) {
					StyleProfile profile = brandVoiceService.getProfile(profileId).orElse(null);
					writeJson(profileFile, profile != null ? profile : new JsonObject());
					steps.add("brand-profile");
					LOG.info("Warmup: trained & cached brand profile {} -> {}", profileId, profileFile);
				} else {
					steps.add("brand-profile-failed");
					LOG.warn("Warmup: brand profile not created");
				}
			} else {
				steps.add("brand-profile-skipped");
				LOG.info("Warmup: brand_profile.json exists; skipped (use force=true to rebuild)");
			}
			timings.put("brandMs", System.currentTimeMillis() - t3);

			out.addProperty("success", true);
			out.addProperty("profileId", profileId);
			out.add("timings", GSON.toJsonTree(timings));
			out.add("steps", GSON.toJsonTree(steps));
			out.addProperty("durationMs", System.currentTimeMillis() - start);
		} catch (Exception e) {
			LOG.error("Warmup failed", e);
			out.addProperty("success", false);
			out.addProperty("error", e.getMessage());
		} finally {
			response.getWriter().write(GSON.toJson(out));
		}
	}

	private Path ensureOutputDir() throws IOException {
		String home = slingSettings != null ? slingSettings.getSlingHomePath() : null;
		File base = StringUtils.isNotBlank(home) ? new File(home) : new File(System.getProperty("java.io.tmpdir"));
		File dir = new File(base, "var/aem-ai-content-copilot");
		if (!dir.exists() && !dir.mkdirs()) {
			throw new IOException("Failed to create output directory: " + dir.getAbsolutePath());
		}
		return dir.toPath();
	}

	private void writeJson(Path file, Object data) throws IOException {
		if (data == null) data = new JsonObject();
		byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
		Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}
}
