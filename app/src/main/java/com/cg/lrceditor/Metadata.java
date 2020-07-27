package com.cg.lrceditor;

import java.io.Serializable;

class Metadata implements Serializable {
	private String artistName = "";
	private String albumName = "";
	private String songName = "";
	private String composerName = "";
	private String creatorName = "";

	String getSongName() {
		return this.songName;
	}

	void setSongName(String songName) {
		this.songName = songName;
	}

	String getArtistName() {
		return this.artistName;
	}

	void setArtistName(String artistName) {
		this.artistName = artistName;
	}

	String getAlbumName() {
		return this.albumName;
	}

	void setAlbumName(String albumName) {
		this.albumName = albumName;
	}

	String getComposerName() {
		return this.composerName;
	}

	void setComposerName(String composerName) {
		this.composerName = composerName;
	}

	String getCreatorName() {
		return this.creatorName;
	}

	void setCreatorName(String creatorName) {
		this.creatorName = creatorName;
	}
}
