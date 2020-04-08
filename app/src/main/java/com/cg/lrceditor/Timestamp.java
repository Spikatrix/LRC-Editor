package com.cg.lrceditor;

import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Timestamp implements Serializable {

	/* Sets the value of the timestamp to MAX_TIMESTAMP_VALUE when it exceeds it */
	static final long MAX_TIMESTAMP_VALUE = 5999999; /* 99:59:999 in milliseconds */
	private long minutes;
	private long seconds;
	private long milliseconds;

	Timestamp(String timestamp) {
		if (!timestamp.matches("^(\\d\\d[:.]\\d\\d[:.]\\d\\d\\d?)$")) {
			throw new IllegalArgumentException("Invalid timestamp format");
		}

		String[] str = timestamp.split("[:.]");

		try {
			this.minutes = Integer.parseInt(str[0]);
			this.seconds = Integer.parseInt(str[1]);
			this.milliseconds = Integer.parseInt(str[2]);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid timestamp format");
		}

		if (this.seconds >= 60) {
			throw new IllegalArgumentException("Seconds must be less than 60");
		}

		if (this.milliseconds < 100) {
			milliseconds *= 10;
		}

		setTime(min(toMilliseconds(), MAX_TIMESTAMP_VALUE));
	}

	Timestamp(long minutes, long seconds, long milliseconds) {
		if (minutes < 0 || seconds < 0 || milliseconds < 0) {
			throw new IllegalArgumentException("Negative arguments");
		}

		this.minutes = minutes;
		this.seconds = seconds;
		this.milliseconds = milliseconds;

		setTime(min(toMilliseconds(), MAX_TIMESTAMP_VALUE));
	}

	Timestamp(long milliseconds) {
		if (milliseconds < 0) {
			throw new IllegalArgumentException("Negative timestamp");
		}

		setTime(min(milliseconds, MAX_TIMESTAMP_VALUE));
	}

	Timestamp(Timestamp timestamp) {
		this.minutes = timestamp.minutes;
		this.seconds = timestamp.seconds;
		this.milliseconds = timestamp.milliseconds;
	}

	void alterTimestamp(long milliseconds) {
		long newTime = toMilliseconds() + milliseconds;

		newTime = max(newTime, 0);
		newTime = min(newTime, MAX_TIMESTAMP_VALUE);

		setTime(newTime);
	}

	long toMilliseconds() {
		return getMinutesInMilliseconds()
			+ getSecondsInMilliseconds()
			+ getMilliseconds();
	}

	long getMinutes() {
		return this.minutes;
	}

	void setMinutes(long minute) {
		this.minutes = minute;
	}

	long getSeconds() {
		return this.seconds;
	}

	void setSeconds(long seconds) {
		this.seconds = seconds;
	}

	long getMilliseconds() {
		return this.milliseconds;
	}

	void setMilliseconds(long milliseconds) {
		this.milliseconds = milliseconds;
	}

	long getMinutesInMilliseconds() {
		return TimeUnit.MINUTES.toMillis(this.minutes);
	}

	long getSecondsInMilliseconds() {
		return TimeUnit.SECONDS.toMillis(this.seconds);
	}

	void setTime(long minutes, long seconds, long milliseconds) {
		setMinutes(minutes);
		setSeconds(seconds);
		setMilliseconds(milliseconds);
	}

	void setTime(long milliseconds) {
		setMinutes(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
		setSeconds(TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(this.minutes));
		setMilliseconds(milliseconds - getSecondsInMilliseconds() - getMinutesInMilliseconds());
	}

	String toStringWithThreeDigitMilliseconds() {
		return String.format(Locale.getDefault(), "%02d:%02d.%03d", this.minutes, this.seconds, this.milliseconds);
	}

	@Override
	public String toString() {
		String str = String.format(Locale.getDefault(), "%02d:%02d.%03d", this.minutes, this.seconds, this.milliseconds);
		return str.substring(0, str.length() - 1); // Display only the first two digits of the milliseconds
	}
}
