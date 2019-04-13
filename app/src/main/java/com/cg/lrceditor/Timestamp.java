package com.cg.lrceditor;

import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Timestamp implements Serializable {

    /* This class sets the value of the timestamp to MAX_TIMESTAMP_VALUE when it exceeds it */
    public static final long MAX_TIMESTAMP_VALUE = 5999999; /* 99:59:999 in milliseconds */
    private long minutes;
    private long seconds;
    private long milliseconds;

    Timestamp(String timestamp) {
        if (!timestamp.matches("^(\\d\\d[:.]\\d\\d[:.]\\d\\d\\d?)$")) {
            throw new IllegalArgumentException("Invalid timestamp format");
        }

        String str[] = timestamp.split("[:.]");

        try {
            this.minutes = Integer.parseInt(str[0]);
            this.seconds = Integer.parseInt(str[1]);
            this.milliseconds = Integer.parseInt(str[2]);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid timestamp format");
        }

        if (this.seconds >= 60) {
            throw new IllegalArgumentException("Seconds cannot be greater than or equal to 60");
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

    public void alterTimestamp(long milliseconds) {
        long newTime = toMilliseconds() + milliseconds;

        newTime = max(newTime, 0);
        newTime = min(newTime, MAX_TIMESTAMP_VALUE);

        setTime(newTime);
    }

    public long toMilliseconds() {
        return getMinutesInMilliseconds()
                + getSecondsInMilliseconds()
                + getMilliseconds();
    }

    public long getMinutes() {
        return this.minutes;
    }

    public void setMinutes(long minute) {
        this.minutes = minute;
    }

    public long getSeconds() {
        return this.seconds;
    }

    public void setSeconds(long seconds) {
        this.seconds = seconds;
    }

    public long getMilliseconds() {
        return this.milliseconds;
    }

    public void setMilliseconds(long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public long getMinutesInMilliseconds() {
        return TimeUnit.MINUTES.toMillis(this.minutes);
    }

    public long getSecondsInMilliseconds() {
        return TimeUnit.SECONDS.toMillis(this.seconds);
    }

    public void setTime(long minutes, long seconds, long milliseconds) {
        setMinutes(minutes);
        setSeconds(seconds);
        setMilliseconds(milliseconds);
    }

    public void setTime(long milliseconds) {
        setMinutes(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
        setSeconds(TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(this.minutes));
        setMilliseconds(milliseconds - getSecondsInMilliseconds() - getMinutesInMilliseconds());
    }

    @Override
    public String toString() {
        String str = String.format(Locale.getDefault(), "%02d:%02d.%03d", this.minutes, this.seconds, this.milliseconds);
        return str.substring(0, str.length() - 1); // Display only the first two digits of the milliseconds
    }
}
