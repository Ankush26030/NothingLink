package com.ankush.amzplugin.plugin;

import com.ankush.amzplugin.AmazonMusicSourceManager;
import com.ankush.amzplugin.plugin.config.AmzPluginConfig;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("amzPluginService")
public class AmzPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {
	private static final Logger log = LoggerFactory.getLogger(AmzPlugin.class);
	private AudioPlayerManager manager;
	private AmazonMusicSourceManager amazonMusic;
	private final AmzPluginConfig config;

	public AmzPlugin(AmzPluginConfig config) {
		log.info("Loading Amazon Music Plugin...");
		this.config = config;
		if (config.isEnabled()) {
			this.amazonMusic = new AmazonMusicSourceManager(config.getProviders(), unused -> manager);
			int sl = Math.max(0, Math.min(10, config.getSearchLimit()));
			this.amazonMusic.setSearchLimit(sl);
			log.info("Amazon Music source enabled with search limit: {}", sl);
		} else { log.info("Amazon Music source is disabled"); }
	}

	@NotNull @Override public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
		this.manager = manager;
		if (amazonMusic != null && config.isEnabled()) { log.info("Registering Amazon Music audio source manager..."); manager.registerSourceManager(amazonMusic); }
		return manager;
	}

	@NotNull @Override public SearchManager configure(@NotNull SearchManager manager) {
		if (amazonMusic != null && config.isEnabled()) { log.info("Registering Amazon Music search manager..."); manager.registerSearchManager(amazonMusic); }
		return manager;
	}
}
