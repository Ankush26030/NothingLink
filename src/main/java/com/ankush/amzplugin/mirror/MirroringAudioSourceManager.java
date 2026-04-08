package com.ankush.amzplugin.mirror;

import com.ankush.amzplugin.ExtendedAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class MirroringAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {
	public static final String ISRC_PATTERN = "%ISRC%";
	public static final String QUERY_PATTERN = "%QUERY%";
	private static final Logger log = LoggerFactory.getLogger(MirroringAudioSourceManager.class);
	protected final Function<Void, AudioPlayerManager> audioPlayerManager;
	protected final MirroringAudioTrackResolver resolver;
	protected final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

	protected MirroringAudioSourceManager(AudioPlayerManager apm, MirroringAudioTrackResolver resolver) { this(unused -> apm, resolver); }
	protected MirroringAudioSourceManager(Function<Void, AudioPlayerManager> apm, MirroringAudioTrackResolver resolver) { this.audioPlayerManager = apm; this.resolver = resolver; }

	@Override public void configureRequests(Function<RequestConfig, RequestConfig> c) { httpInterfaceManager.configureRequests(c); }
	@Override public void configureBuilder(Consumer<HttpClientBuilder> c) { httpInterfaceManager.configureBuilder(c); }
	public HttpInterface getHttpInterface() { return httpInterfaceManager.getInterface(); }
	@Override public void shutdown() { try { httpInterfaceManager.close(); } catch (IOException e) { log.error("Failed to close HTTP interface manager", e); } }
	public AudioPlayerManager getAudioPlayerManager() { return audioPlayerManager.apply(null); }
	public MirroringAudioTrackResolver getResolver() { return resolver; }
}
