package com.cg.lrceditor;

import android.os.Environment;

class Constants {
    static final int WRITE_EXTERNAL_REQUEST = 1;
    static final int FILE_REQUEST = 2;
    static final int READ_LOCATION_REQUEST = 3;
    static final int SAVE_LOCATION_REQUEST = 4;

    static final String ERROR_COLOR = "#c61b1b";
    static final String SUCCESS_COLOR = "#2da81a";
    static final String HOMEPAGE_TIMESTAMP_COLOR = "#2bb1e2";
    static final String HOMEPAGE_LYRIC_COLOR = "#dd9911";
    /* See bg_list_row for colors of list items */

    static final long MAX_TIMESTAMP_VALUE = Timestamp.MAX_TIMESTAMP_VALUE;

    static final String defaultLocation = Environment.getExternalStorageDirectory().getPath() + "/Lyrics";
}
