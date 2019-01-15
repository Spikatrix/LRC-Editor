package com.cg.lrceditor;

import java.io.Serializable;

public class SongMetaData implements Serializable {
    private String artistName = "";
    private String albumName = "";
    private String songName = "";
    private String composerName = "";

    public String getSongName() {
        return this.songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public String getArtistName() {
        return this.artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getAlbumName() {
        return this.albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getComposerName() {
        return this.composerName;
    }

    public void setComposerName(String composerName) {
        this.composerName = composerName;
    }
}
