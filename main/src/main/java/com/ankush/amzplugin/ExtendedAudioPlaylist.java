package com.ankush.amzplugin;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import java.util.List;

public class ExtendedAudioPlaylist extends BasicAudioPlaylist {
	protected final Type type;
	protected final String url, artworkURL, author;
	protected final Integer totalTracks;

	public ExtendedAudioPlaylist(String name, List<AudioTrack> tracks, Type type, String url, String artworkURL, String author, Integer totalTracks) {
		super(name, tracks, null, false);
		this.type = type; this.url = url; this.artworkURL = artworkURL; this.author = author; this.totalTracks = totalTracks;
	}

	public Type getType() { return type; }
	public String getUrl() { return url; }
	public String getArtworkURL() { return artworkURL; }
	public String getAuthor() { return author; }
	public Integer getTotalTracks() { return totalTracks; }

	public enum Type {
		ALBUM("album"), PLAYLIST("playlist"), ARTIST("artist"), RECOMMENDATIONS("recommendations");
		public final String name;
		Type(String name) { this.name = name; }
	}
}
