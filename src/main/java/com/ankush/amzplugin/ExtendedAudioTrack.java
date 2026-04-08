package com.ankush.amzplugin;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;

public abstract class ExtendedAudioTrack extends DelegatedAudioTrack {
	protected final String albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl;
	protected final boolean isPreview;

	public ExtendedAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview) {
		super(trackInfo);
		this.albumName = albumName; this.albumUrl = albumUrl; this.artistUrl = artistUrl;
		this.artistArtworkUrl = artistArtworkUrl; this.previewUrl = previewUrl; this.isPreview = isPreview;
	}

	public String getAlbumName() { return albumName; }
	public String getAlbumUrl() { return albumUrl; }
	public String getArtistUrl() { return artistUrl; }
	public String getArtistArtworkUrl() { return artistArtworkUrl; }
	public String getPreviewUrl() { return previewUrl; }
	public boolean isPreview() { return isPreview; }
}
