package com.ankush.amzplugin.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMirroringAudioTrackResolver implements MirroringAudioTrackResolver {
	private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackResolver.class);
	private static final String SPOTIFY_SEARCH_PREFIX = "spsearch:";
	private static final String APPLE_MUSIC_SEARCH_PREFIX = "amsearch:";

	private String[] providers = { "ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"", "ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN };

	public DefaultMirroringAudioTrackResolver(String[] providers) {
		if (providers != null && providers.length > 0) this.providers = providers;
	}

	@Override
	public AudioItem apply(MirroringAudioTrack mirroringAudioTrack) {
		for (var provider : providers) {
			if (provider.startsWith(SPOTIFY_SEARCH_PREFIX)) { log.warn("Can not use spotify search as provider!"); continue; }
			if (provider.startsWith(APPLE_MUSIC_SEARCH_PREFIX)) { log.warn("Can not use apple music search as provider!"); continue; }
			if (provider.contains(MirroringAudioSourceManager.ISRC_PATTERN)) {
				if (mirroringAudioTrack.getInfo().isrc != null && !mirroringAudioTrack.getInfo().isrc.isEmpty()) {
					provider = provider.replace(MirroringAudioSourceManager.ISRC_PATTERN, mirroringAudioTrack.getInfo().isrc.replace("-", ""));
				} else { continue; }
			}
			provider = provider.replace(MirroringAudioSourceManager.QUERY_PATTERN, getTrackTitle(mirroringAudioTrack));
			AudioItem item;
			try { item = mirroringAudioTrack.loadItem(provider); } catch (Exception e) { log.error("Failed to load from \"{}\"", provider, e); continue; }
			if (item instanceof AudioPlaylist && ((AudioPlaylist) item).getTracks().isEmpty() || item == AudioReference.NO_TRACK) continue;
			return item;
		}
		return AudioReference.NO_TRACK;
	}

	public String getTrackTitle(MirroringAudioTrack t) {
		var q = t.getInfo().title;
		if (!t.getInfo().author.equals("unknown")) q += " " + t.getInfo().author;
		return q;
	}
}
