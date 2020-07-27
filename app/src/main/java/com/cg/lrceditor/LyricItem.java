package com.cg.lrceditor;

import java.io.Serializable;

class LyricItem implements Serializable {
	boolean isSelected = false;
	private Timestamp timestamp;
	private String lyric;

	LyricItem(String lyric, Timestamp timestamp) {
		this.lyric = lyric;
		this.timestamp = timestamp;
	}

	Timestamp getTimestamp() {
		return timestamp;
	}

	void setTimestamp(Timestamp timestamp) {
		this.timestamp = timestamp;
	}

	String getLyric() {
		return lyric;
	}

	void setLyric(String lyric) {
		this.lyric = lyric;
	}
}
