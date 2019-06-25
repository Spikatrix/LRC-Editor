package com.cg.lrceditor;

import android.os.Environment;

public class Constants {
    public static final int WRITE_EXTERNAL_REQUEST = 1;
    public static final int FILE_REQUEST = 2;
    public static final int READ_LOCATION_REQUEST = 3;
    public static final int SAVE_LOCATION_REQUEST = 4;

    public static final String ERROR_COLOR = "#c61b1b";
    public static final String SUCCESS_COLOR = "#2da81a";
    public static final String HOMEPAGE_TIMESTAMP_COLOR = "#2bb1e2";
    public static final String HOMEPAGE_LYRIC_COLOR = "#dd9911";
    /* See bg_list_row for colors of list items */

    public static final long MAX_TIMESTAMP_VALUE = Timestamp.MAX_TIMESTAMP_VALUE;

    public static final String defaultLocation = Environment.getExternalStorageDirectory().getPath() + "/Lyrics";
}
