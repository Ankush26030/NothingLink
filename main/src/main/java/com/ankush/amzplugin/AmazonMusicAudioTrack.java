package com.ankush.amzplugin;

import com.ankush.amzplugin.mirror.MirroringAudioSourceManager;
import com.ankush.amzplugin.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

public class AmazonMusicAudioTrack extends MirroringAudioTrack {
	public AmazonMusicAudioTrack(AudioTrackInfo trackInfo, AmazonMusicSourceManager sm) { this(trackInfo, null, null, null, null, null, false, sm); }
	public AmazonMusicAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sm) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sm);
	}
	@Override protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) { return new Mp3AudioTrack(trackInfo, stream); }
	@Override protected AudioTrack makeShallowClone() {
		return new AmazonMusicAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, (AmazonMusicSourceManager) sourceManager);
	}
}
