package com.ankush.amzplugin.plugin;

import com.ankush.amzplugin.AmazonMusicSourceManager;
import com.ankush.amzplugin.ig.IGSourceManager;
import com.ankush.amzplugin.pd.PandoraProvider;
import com.ankush.amzplugin.plugin.config.AmzPluginConfig;
import com.ankush.amzplugin.plugin.config.InstagramPluginConfig;
import com.ankush.amzplugin.plugin.config.NothingLinkConfig;
import com.ankush.amzplugin.plugin.config.PandoraPluginConfig;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service("amzPluginService")
public class AmzPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {
	private static final Logger log = LoggerFactory.getLogger(AmzPlugin.class);
	private static final String VERSION = "1.0.6";

	private AudioPlayerManager manager;
	private AmazonMusicSourceManager amazonMusic;
	private IGSourceManager igSource;
	private PandoraProvider pandoraSource;
	private DiscordWebhookLogger webhook;
	private NothingLinkAudioEventListener audioEventListener;
	private final AmzPluginConfig config;
	private final InstagramPluginConfig igConfig;
	private final PandoraPluginConfig pdConfig;
	private final NothingLinkConfig nlConfig;

	public AmzPlugin(AmzPluginConfig config, InstagramPluginConfig igConfig, PandoraPluginConfig pdConfig,
			NothingLinkConfig nlConfig) {
		log.info("Loading NothingLink Plugin v{}...", VERSION);
		this.config = config;
		this.igConfig = igConfig;
		this.pdConfig = pdConfig;
		this.nlConfig = nlConfig;

		// ── Webhook Logger Setup ──────────────────────────────────────
		if (nlConfig.isWebhookEnabled() && nlConfig.getWebhookUrl() != null && !nlConfig.getWebhookUrl().isEmpty()) {
			this.webhook = new DiscordWebhookLogger(
					nlConfig.getWebhookUrl(),
					nlConfig.getWebhookBotName(),
					null);
			this.webhook.setSearchLoggingEnabled(nlConfig.isLogSearchEvents());
			log.info("Discord webhook logger enabled (bot={}, trackEvents={}, searchEvents={})",
					nlConfig.getWebhookBotName(), nlConfig.isLogTrackEvents(), nlConfig.isLogSearchEvents());

			// Create audio event listener if track events are enabled
			if (nlConfig.isLogTrackEvents()) {
				this.audioEventListener = new NothingLinkAudioEventListener(webhook);
				log.info("Audio event listener created for webhook logging");
			}
		}

		// ── Source Manager Initialization ─────────────────────────────
		Map<String, Boolean> sourceStatus = new LinkedHashMap<>();

		if (config.isEnabled()) {
			this.amazonMusic = new AmazonMusicSourceManager(config.getProviders(), unused -> manager);
			int sl = Math.max(0, Math.min(10, config.getSearchLimit()));
			this.amazonMusic.setSearchLimit(sl);
			if (webhook != null)
				this.amazonMusic.setWebhook(webhook);
			log.info("Amazon Music source enabled (searchLimit={})", sl);
			sourceStatus.put("Amazon Music (limit=" + sl + ")", true);
		} else {
			log.info("Amazon Music source is disabled");
			sourceStatus.put("Amazon Music", false);
		}

		if (igConfig.isEnabled()) {
			this.igSource = new IGSourceManager();
			if (webhook != null)
				this.igSource.setWebhook(webhook);
			log.info("Instagram source enabled");
			sourceStatus.put("Instagram", true);
		} else {
			log.info("Instagram source is disabled");
			sourceStatus.put("Instagram", false);
		}

		if (pdConfig.isEnabled()) {
			this.pandoraSource = new PandoraProvider(pdConfig.getProviders(), pdConfig.getTokenApiUrl(),
					pdConfig.getCsrfToken(), pdConfig.isPreferTokenApi(), pdConfig.getSearchLimit(), unused -> manager);
			if (webhook != null)
				this.pandoraSource.setWebhook(webhook);
			log.info("Pandora source enabled (searchLimit={})", pdConfig.getSearchLimit());
			sourceStatus.put("Pandora (limit=" + pdConfig.getSearchLimit() + ")", true);
		} else {
			log.info("Pandora source is disabled");
			sourceStatus.put("Pandora", false);
		}

		// ── Webhook: Log Startup ──────────────────────────────────────
		if (webhook != null) {
			webhook.logStartup(
					VERSION,
					sourceStatus,
					System.getProperty("java.version"),
					null // Lavalink version not available at this point
			);
		}

		// ── Register shutdown hook for crash logging ──────────────────
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (webhook != null) {
				log.info("JVM shutdown hook triggered — sending final webhook...");
				webhook.logShutdown(VERSION, "JVM Shutdown Hook (possible crash or SIGTERM)");
				webhook.shutdown();
			}
		}, "NothingLink-ShutdownHook"));

		log.info("NothingLink Plugin v{} loaded successfully", VERSION);
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
		this.manager = manager;
		Map<String, String> registeredDetails = new LinkedHashMap<>();

		if (amazonMusic != null && config.isEnabled()) {
			log.info("Registering Amazon Music source...");
			manager.registerSourceManager(amazonMusic);
			registeredDetails.put("Amazon Music",
					"✅ Registered\n🔍 Search: `amzsearch:`\n📊 Limit: `" + config.getSearchLimit() + "`");
		}
		if (igSource != null && igConfig.isEnabled()) {
			log.info("Registering Instagram source...");
			manager.registerSourceManager(igSource);
			registeredDetails.put("Instagram", "✅ Registered\n🔗 Supports: Reels, Posts");
		}
		if (pandoraSource != null && pdConfig.isEnabled()) {
			log.info("Registering Pandora source...");
			manager.registerSourceManager(pandoraSource);
			registeredDetails.put("Pandora", "✅ Registered\n📊 Limit: `" + pdConfig.getSearchLimit()
					+ "`\n🔑 Token API: " + (pdConfig.isPreferTokenApi() ? "Yes" : "No"));
		}

		// Log source registration to webhook
		if (webhook != null && !registeredDetails.isEmpty()) {
			webhook.logSourcesRegistered(registeredDetails);
		}

		return manager;
	}

	@NotNull
	@Override
	public SearchManager configure(@NotNull SearchManager manager) {
		if (amazonMusic != null && config.isEnabled()) {
			log.info("Registering Amazon Music search provider...");
			manager.registerSearchManager(amazonMusic);

			if (webhook != null) {
				webhook.info("🔍 Search Provider Registered",
						"Amazon Music search provider has been registered with LavaSearch.");
			}
		}
		return manager;
	}

	/**
	 * Get the webhook logger instance (for use by other components).
	 */
	public DiscordWebhookLogger getWebhook() {
		return webhook;
	}

	/**
	 * Get the audio event listener (for attaching to players).
	 */
	public NothingLinkAudioEventListener getAudioEventListener() {
		return audioEventListener;
	}

	/**
	 * Spring lifecycle: called before the bean is destroyed.
	 * Sends a graceful shutdown log with session statistics.
	 */
	@PreDestroy
	public void destroy() {
		log.info("NothingLink Plugin v{} shutting down...", VERSION);
		if (webhook != null) {
			webhook.logShutdown(VERSION, "Graceful Spring Context Shutdown");
			webhook.shutdown();
		}
		log.info("NothingLink Plugin v{} shut down complete.", VERSION);
	}
}