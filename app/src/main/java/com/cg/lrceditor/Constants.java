package com.cg.lrceditor;

import android.os.Environment;

public class Constants {
    public static final int WRITE_EXTERNAL_REQUEST = 1;
    public static final int FILE_REQUEST = 2;
    public static final int LOCATION_REQUEST = 3;

    public static final long MAX_TIMESTAMP_VALUE = Timestamp.MAX_TIMESTAMP_VALUE;

    public static final String defaultSaveLocation = Environment.getExternalStorageDirectory().getPath() + "/Lyrics";
}
