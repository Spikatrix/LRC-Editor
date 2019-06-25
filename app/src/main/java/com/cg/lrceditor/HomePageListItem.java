package com.cg.lrceditor;

import java.io.File;

public class HomePageListItem {
    public File file;
    public SongMetaData songMetaData;
    public String[] lyrics;

    public boolean isExpanded = false;
    public boolean isSelected = false;

    HomePageListItem(File file, SongMetaData songMetaData, String[] lyrics) {
        this.file = file;
        this.songMetaData = songMetaData;
        this.lyrics = lyrics;
    }
}
