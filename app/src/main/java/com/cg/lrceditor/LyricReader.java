package com.cg.lrceditor;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LyricReader {

    private String[] lyrics = null;
    private String[] timestamps = null;

    private SongMetaData songMetaData = new SongMetaData();

    private String errorMsg;

    private File f = null;

    private InputStream in = null;

    LyricReader(String path, String filename) {
        f = new File(path, filename);
    }

    LyricReader(Uri uri, Context c) {
        try {
            this.in = c.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errorMsg = "Oops! File not found! \n" + e.getMessage();
        }
    }

    public boolean readLyrics() {
        try {
            DataInputStream in;
            if (f != null) {
                FileInputStream fis = new FileInputStream(f);
                in = new DataInputStream(fis);
            } else if (this.in != null) {
                in = new DataInputStream(this.in);
            } else {
                return false;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            StringBuilder contents = new StringBuilder();
            String temp;
            while ((temp = br.readLine()) != null) {
                contents.append(temp);
                contents.append('\n');
            }

            in.close();

            StringBuilder lyrics = new StringBuilder();
            List<String> timestamps = new ArrayList<>();
            int count, extras = 0;
            boolean needToSort = false;
            for (String line : contents.toString().split("\\n")) {
                count = 0;
                extras = 0;
                while (true) {
                    temp = line.substring(count * 10 + extras);

                    //TODO: Very big timestamps won't be read

                    if (temp.matches("^(\\[\\d\\d[:.]\\d\\d[:.]\\d\\d\\d?]).*$")) {
                        if (temp.charAt(9) != ']')
                            extras++;
                        timestamps.add(temp.substring(1, 9));
                        count++;
                    } else if (songMetaData.getSongName().isEmpty() && temp.matches("^\\[ti:.*]$")) {
                        songMetaData.setSongName(temp.substring(4, temp.length() - 1).trim());
                        break;
                    } else if (songMetaData.getArtistName().isEmpty() && temp.matches("^\\[ar:.*]$")) {
                        songMetaData.setArtistName(temp.substring(4, temp.length() - 1).trim());
                        break;
                    } else if (songMetaData.getAlbumName().isEmpty() && temp.matches("^\\[al:.*]$")) {
                        songMetaData.setAlbumName(temp.substring(4, temp.length() - 1).trim());
                        break;
                    } else if (songMetaData.getComposerName().isEmpty() && temp.matches("^\\[au:.*]$")) {
                        songMetaData.setComposerName(temp.substring(4, temp.length() - 1).trim());
                        break;
                    } else break;
                }
                if (count > 1) {
                    needToSort = true;
                }

                if (temp.trim().isEmpty())
                    temp = " ";

                for (int i = 0; i < count; i++) {
                    lyrics.append(temp);
                    lyrics.append("\n");
                }
            }

            if (lyrics.toString().equals("")) {
                errorMsg = "Couldn't parse lyrics from the file. Check if the lrc file is properly formatted";
                return false;
            }

            String[] items = lyrics.toString().split("\\n");
            int size = items.length;
            this.lyrics = new String[size];
            this.timestamps = new String[size];

            for (int i = 0; i < size; i++) {
                this.lyrics[i] = items[i];
                this.timestamps[i] = timestamps.get(i);
            }

            if (needToSort) {
                sortLyrics();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errorMsg = "Oops! File not found! \n" + e.getMessage();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            errorMsg = "Oops! An error occurred while reading! \n" + e.getMessage();
            return false;
        }

        return true;
    }

    private void sortLyrics() {
        int size = timestamps.length;
        int[] priority = new int[size];
        for (int i = 0; i < size; i++)
            priority[i] = valueOf(i);

        quickSort(0, size - 1, priority);
    }

    private void quickSort(int lowerIndex, int higherIndex, int[] array) {
        int i = lowerIndex;
        int j = higherIndex;

        int pivot = array[lowerIndex + (higherIndex - lowerIndex) / 2];

        while (i <= j) {
            while (array[i] < pivot) {
                i++;
            }
            while (array[j] > pivot) {
                j--;
            }
            if (i <= j) {
                swap(i, j, array);
                i++;
                j--;
            }
        }

        if (lowerIndex < j)
            quickSort(lowerIndex, j, array);
        if (i < higherIndex)
            quickSort(i, higherIndex, array);
    }

    private void swap(int i, int j, int[] array) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;

        String s = lyrics[i];
        lyrics[i] = lyrics[j];
        lyrics[j] = s;

        s = timestamps[i];
        timestamps[i] = timestamps[j];
        timestamps[j] = s;
    }


    private int valueOf(int position) {
        return (int) (TimeUnit.MINUTES.toMillis(Integer.parseInt(this.timestamps[position].substring(0, 2))) +
                TimeUnit.SECONDS.toMillis(Integer.parseInt(this.timestamps[position].substring(3, 5))) +
                Integer.parseInt(this.timestamps[position].substring(6, 8)));
    }

    public String[] getLyrics() {
        return this.lyrics;
    }

    public String[] getTimestamps() {
        return this.timestamps;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public SongMetaData getSongMetaData() {
        return songMetaData;
    }
}
