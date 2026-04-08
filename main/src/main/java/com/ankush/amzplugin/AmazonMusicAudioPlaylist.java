package com.ankush.amzplugin;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.List;

public class AmazonMusicAudioPlaylist extends ExtendedAudioPlaylist {
	public AmazonMusicAudioPlaylist(String name, List<AudioTrack> tracks, Type type, String url, String artworkURL, String author, Integer totalTracks) {
		super(name, tracks, type, url, artworkURL, author, totalTracks);
	}
}
