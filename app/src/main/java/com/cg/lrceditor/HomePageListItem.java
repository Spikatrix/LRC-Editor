package com.cg.lrceditor;

import java.io.File;

public class HomePageListItem {
	public File file;
	public String[] lyrics;
	SongMetaData songMetaData;
	boolean isExpanded = false;
	boolean isSelected = false;

	HomePageListItem(File file, SongMetaData songMetaData, String[] lyrics) {
		this.file = file;
		this.songMetaData = songMetaData;
		this.lyrics = lyrics;
	}
}
