package com.cg.lrceditor;

import java.io.File;

public class HomePageListItem {
	public File file;
	public String[] lyrics;
	Metadata metadata;
	boolean isExpanded = false;
	boolean isSelected = false;

	HomePageListItem(File file, Metadata metadata, String[] lyrics) {
		this.file = file;
		this.metadata = metadata;
		this.lyrics = lyrics;
	}
}
