package com.cg.lrceditor;

import android.os.Environment;

class Constants {
	static final int WRITE_EXTERNAL_REQUEST = 1;
	static final int FILE_REQUEST = 2;
	static final int READ_LOCATION_REQUEST = 3;
	static final int SAVE_LOCATION_REQUEST = 4;

	static final long MAX_TIMESTAMP_VALUE = Timestamp.MAX_TIMESTAMP_VALUE;

	static final String defaultLocation = Environment.getExternalStorageDirectory().getPath() + "/Lyrics";

	static final String READ_LOCATION_PREFERENCE = "readLocation";
	static final String SAVE_LOCATION_PREFERENCE = "saveLocation";
	static final String TIMESTAMP_STEP_AMOUNT_PREFERENCE = "timestamp_step_amount";
	static final String THREE_DIGIT_MILLISECONDS_PREFERENCE = "three_digit_milliseconds";
	static final String THEME_PREFERENCE = "current_theme";
	static final String PURCHASED_PREFERENCE = "lrceditor_purchased";
}
