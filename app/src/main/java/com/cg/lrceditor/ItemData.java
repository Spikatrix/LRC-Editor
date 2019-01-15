package com.cg.lrceditor;

import java.io.Serializable;

public class ItemData implements Serializable {
    private String timestamp;
    private String lyric;

    public ItemData(String lyric, String timestamp) {
        this.lyric = lyric;
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }
}
