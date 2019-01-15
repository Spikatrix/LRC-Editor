package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class EditorActivity extends AppCompatActivity implements LyricListAdapter.ItemClickListener,
        MediaPlayer.OnPreparedListener,
        SeekBar.OnSeekBarChangeListener,
        MediaPlayer.OnCompletionListener {

    private static final int FILE_REQUEST = 1;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager linearLayoutManager;
    private LyricListAdapter mAdapter;

    private boolean isPlaying = false;
    private boolean updateBusy = false;
    private boolean playerPrepared = false;
    private boolean stopUpdating = false;
    private boolean firstStart = true;
    private boolean changedData = false;

    private boolean isDarkTheme = false;

    private Uri uri = null;
    private String lrcFileName = null;
    private String songFileName = null;

    private SongMetaData songMetaData = null;

    private ItemData[] clipboard = null;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    private Handler songTimeUpdater = new Handler();
    private SeekBar seekbar;
    private MediaPlayer player;

    private Handler timestampUpdater = new Handler();
    private int longPressed = 0;
    private int longPressedPos = -1;

    private TextView startText, endText;
    private TextView titleText;
    private ImageButton play_pause;

    private Handler flasher = new Handler();
    private boolean flashCheck = false;
    private Runnable flash = new Runnable() {
        @Override
        public void run() {
            if (!flashCheck)
                return;

            int time = player.getCurrentPosition();
            int first = linearLayoutManager.findFirstVisibleItemPosition();
            int last = linearLayoutManager.findLastVisibleItemPosition();

            int pos = first;
            if (first == -1 || last == -1) {
                flashCheck = false;
                return;
            }
            SparseBooleanArray s = mAdapter.getFlashingItems();
            while (pos <= last) {
                String timestamp;
                try {
                    timestamp = mAdapter.lyricData.get(pos).getTimestamp();
                } catch (IndexOutOfBoundsException ignored) {
                    pos++;
                    continue;
                }
                if (timestamp == null) {
                    pos++;
                    continue;
                }
                int currTime = timeToMilli(timestamp);
                int diff = time - currTime;
                if (diff <= 100 && diff >= 0 && s.indexOfKey(pos) < 0) {
                    ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(true);
                    mAdapter.startFlash(pos);
                    flasher.postDelayed(new stopFlash(pos), 450);
                }
                pos++;
            }

            if (isPlaying)
                flasher.postDelayed(this, 20);
        }
    };
    private Runnable updateTimestamp = new Runnable() {
        @Override
        public void run() {
            if (longPressedPos == -1)
                return;

            String time = mAdapter.lyricData.get(longPressedPos).getTimestamp();
            long milli = timeToMilli(time);

            if (longPressed == 1) {
                milli += 100;
            } else if (longPressed == -1) {
                milli -= 100;
            }

            if (milli < 0)
                milli = 0;
            else if (longPressed == 1 && milli + 100 > 5999999) /* 99:59:999 in milliseconds */ {
                longPressed = 0;
                milli = 5999999;
            }

            mAdapter.lyricData.get(longPressedPos).setTimestamp(
                    String.format(Locale.getDefault(), "%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
            mAdapter.notifyItemChanged(longPressedPos);

            if (longPressed != 0 && milli != 0)
                timestampUpdater.postDelayed(this, 50);
            else {
                longPressedPos = -1;
                longPressed = 0;
            }
        }
    };
    private Runnable updateSongTime = new Runnable() {
        @Override
        public void run() {
            if (stopUpdating) {
                return;
            }

            if (!updateBusy) {
                seekbar.setProgress(player.getCurrentPosition());
            }

            songTimeUpdater.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        if (preferences.getString("current_theme", "").equals("dark")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDark_NoActionBar);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        Toolbar toolbar = findViewById(R.id.Editortoolbar);
        setSupportActionBar(toolbar);

        try {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        ArrayList<ItemData> lyricData;

        Intent intent = getIntent();

        if (intent.getData() != null) { /* LRC File opened from elsewhere */
            LyricReader r = new LyricReader(intent.getData(), this);
            if (!r.readLyrics()) {
                Toast.makeText(this, r.getErrorMsg(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            String[] lyrics = r.getLyrics();
            String[] timestamps = r.getTimestamps();
            lyricData = populateDataSet(lyrics, timestamps);

            songMetaData = r.getSongMetaData();

            lrcFileName = getFileName(intent.getData());
        } else {                        /* New LRC file or existing opened from the homepage */
            String[] lyrics = intent.getStringArrayExtra("LYRICS");
            String[] timestamps = intent.getStringArrayExtra("TIMESTAMPS");
            lyricData = populateDataSet(lyrics, timestamps);

            songMetaData = (SongMetaData) intent.getSerializableExtra("SONG METADATA");

            lrcFileName = intent.getStringExtra("LRC FILE NAME");
        }

        mRecyclerView = findViewById(R.id.recyclerview);
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        mAdapter = new LyricListAdapter(this, lyricData);
        if (isDarkTheme) {
            mAdapter.isDarkTheme = true;
        }
        mAdapter.setClickListener(this);
        mRecyclerView.setAdapter(mAdapter);
        linearLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(linearLayoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mRecyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(dividerItemDecoration);

        actionModeCallback = new ActionModeCallback();

        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        seekbar = findViewById(R.id.seekBar);
        startText = findViewById(R.id.startText);
        endText = findViewById(R.id.endText);
        play_pause = findViewById(R.id.play_pause);
        titleText = findViewById(R.id.titleText);

        if (isDarkTheme) {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
            startText.setTextColor(Color.WHITE);
            endText.setTextColor(Color.WHITE);
        }

        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        seekbar.setOnSeekBarChangeListener(this);

        flasher.post(flash);
    }

    private ArrayList<ItemData> populateDataSet(String[] lyrics, String[] timestamps) {
        ArrayList<ItemData> lyricData = new ArrayList<>();

        if (!lyrics[0].trim().isEmpty())
            lyricData.add(new ItemData("", "00:00.00"));

        for (int i = 0, len = lyrics.length; i < len; i++) {
            try {
                lyricData.add(new ItemData(lyrics[i].trim(), timestamps[i].trim()));
            } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                lyricData.add(new ItemData(lyrics[i].trim(), null));
            }
        }

        if (!lyrics[lyrics.length - 1].trim().isEmpty())
            lyricData.add(new ItemData("", null));

        return lyricData;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isPlaying) {
            flashCheck = false;
            playPause(null);
        }
    }

    @Override
    public void onAddButtonClick(int position) {
        double pos;
        if (playerPrepared)
            pos = player.getCurrentPosition();
        else
            pos = 0;

        if (pos > 5999999) {
            Toast.makeText(this, "Timestamps larger than 99:59:999 are currently unsupported", Toast.LENGTH_SHORT).show();
            return;
        }

        changedData = true;

        mAdapter.lyricData.get(position).setTimestamp(
                String.format(Locale.getDefault(), "%02d:%02d.%02d", getMinutes(pos), getSeconds(pos), getMilli(pos)));
        mAdapter.notifyItemChanged(position);
        mRecyclerView.smoothScrollToPosition(position + 1);

        flashCheck = true;
        flasher.post(flash);
    }

    @Override
    public void onPlayButtonClick(int position) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        String time = mAdapter.lyricData.get(position).getTimestamp();
        player.seekTo(timeToMilli(time));
        if (!isPlaying) {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
            player.start();
            isPlaying = true;
        }
        songTimeUpdater.post(updateSongTime);
        flashCheck = true;
        flasher.post(flash);
    }

    @Override
    public void onIncreaseTimeClick(int position) {
        longPressed = 0;
        longPressedPos = -1;

        String time = mAdapter.lyricData.get(position).getTimestamp();
        long milli = timeToMilli(time);
        milli += 100;
        if (milli + 100 > 5999999) /* 99:59:999 in milliseconds */ {
            milli = 5999999;
        }
        mAdapter.lyricData.get(position).setTimestamp(
                String.format(Locale.getDefault(), "%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
        mAdapter.notifyItemChanged(position);

        changedData = true;

        if (playerPrepared) {
            player.seekTo((int) milli);
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
            player.start();
            isPlaying = true;
            songTimeUpdater.post(updateSongTime);
            flashCheck = true;
            flasher.post(flash);
        }
    }

    @Override
    public void onDecreaseTimeClick(int position) {
        longPressed = 0;
        longPressedPos = -1;

        String time = mAdapter.lyricData.get(position).getTimestamp();
        long milli = timeToMilli(time);
        milli -= 100;
        if (milli < 0)
            milli = 0;
        mAdapter.lyricData.get(position).setTimestamp(
                String.format(Locale.getDefault(), "%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
        mAdapter.notifyItemChanged(position);

        changedData = true;

        if (playerPrepared) {
            player.seekTo((int) milli);
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
            player.start();
            isPlaying = true;
            songTimeUpdater.post(updateSongTime);
            flashCheck = true;
            flasher.post(flash);
        }
    }

    @Override
    public void onLongPressIncrTime(int position) {
        longPressed = 1;

        if (longPressedPos == -1) {
            longPressedPos = position;
            timestampUpdater.post(updateTimestamp);
        } else {
            longPressedPos = position;
        }

        changedData = true;
    }

    @Override
    public void onLongPressDecrTime(int position) {
        longPressed = -1;

        if (longPressedPos == -1) {
            longPressedPos = position;
            timestampUpdater.post(updateTimestamp);
        } else {
            longPressedPos = position;
        }

        changedData = true;
    }

    @Override
    public void onLyricItemSelected(int position) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }

        toggleSelection(position);
    }

    @Override
    public void onLyricItemClicked(int position) {
        if (actionMode == null)
            return;

        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        mAdapter.toggleSelection(position);
        int count = mAdapter.getSelectionCount();

        if (count == 0) {
            actionMode.finish();
            actionMode = null;
        } else {
            Menu menu = actionMode.getMenu();
            MenuItem itemEdit = menu.findItem(R.id.action_edit);
            MenuItem insertLyric = menu.findItem(R.id.action_insert);
            MenuItem paste = menu.findItem(R.id.action_paste);
            MenuItem manuallySet = menu.findItem(R.id.action_manually_set);
            if (count >= 2) {
                itemEdit.setVisible(false);
                insertLyric.setVisible(false);
                paste.setVisible(false);
                manuallySet.setVisible(false);
            } else {
                itemEdit.setVisible(true);
                insertLyric.setVisible(true);
                manuallySet.setVisible(true);
                if (clipboard != null) {
                    paste.setVisible(true);
                }
            }

            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    private long getMinutes(double time) {
        return TimeUnit.MILLISECONDS.toMinutes((long) time);
    }

    private long getSeconds(double time) {
        return TimeUnit.MILLISECONDS.toSeconds((long) time) - TimeUnit.MINUTES.toSeconds(getMinutes(time));
    }

    private long getMilli(double time) {

        return ((long) (time - TimeUnit.SECONDS.toMillis(getSeconds(time)) - TimeUnit.MINUTES.toMillis(getMinutes(time)))) / 10;
    }

    private void readyMediaPlayer(Uri songUri) {
        try {
            player.setDataSource(this, songUri);
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(this, "Whoops " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }

        playerPrepared = false;
        uri = songUri;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            titleText.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        } catch (RuntimeException e) {
            e.printStackTrace();
            File f = null;
            try {
                f = new File(uri.getPath());
                titleText.setText(f.getName().substring(0, f.getName().length() - 4));
            } catch (IndexOutOfBoundsException e2) {
                e2.printStackTrace();
                titleText.setText(f.getName());
            } catch (Exception e3) {
                e3.printStackTrace();
                titleText.setText(R.string.title_error_message);
            }
        }

        player.prepareAsync();
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void playPause(View view) {
        if (!playerPrepared) {
            if (view != null) {
                Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (isPlaying) {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
            }
            player.pause();
            flashCheck = false;
        } else {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
            player.start();
            flashCheck = true;
            flasher.post(flash);
        }
        isPlaying = !isPlaying;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playerPrepared = true;
        firstStart = false;
        stopUpdating = false;
        startText.setText(getString(R.string.default_timetext));
        double duration = player.getDuration();
        endText.setText(String.format(Locale.getDefault(), "%02d:%02d", getMinutes(duration), getSeconds(duration)));
        seekbar.setMax((int) duration);
        seekbar.setProgress(0);
        songTimeUpdater.post(updateSongTime);

        songFileName = getFileName(uri);
    }

    public void rewind5(View view) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        player.seekTo(player.getCurrentPosition() - 5 * 1000);
        songTimeUpdater.post(updateSongTime);
    }

    public void forward5(View view) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        player.seekTo(player.getCurrentPosition() + 5 * 1000);
        songTimeUpdater.post(updateSongTime);
    }

    private int timeToMilli(String time) {
        int[] times = new int[3];
        int index = 0;
        for (String str : time.split("[:.]"))
            times[index++] = Integer.parseInt(str);

        return (int) TimeUnit.MINUTES.toMillis(times[0])
                + (int) TimeUnit.SECONDS.toMillis(times[1])
                + (int) (TimeUnit.MILLISECONDS.toMillis(times[2]) * 10);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        startText.setText(String.format(Locale.getDefault(), "%02d:%02d", getMinutes(progress), getSeconds(progress)));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        updateBusy = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        player.seekTo(timeToMilli(startText.getText().toString()));
        updateBusy = false;
        songTimeUpdater.post(updateSongTime);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (isDarkTheme) {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
        } else {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
        }
        isPlaying = false;
        flashCheck = false;
    }

    public void selectSong(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");

        try {
            startActivityForResult(intent, FILE_REQUEST);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open the system song picker dialog; Are you sure you're running Android Kitkat or up?", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();

                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }

                    if (isDarkTheme) {
                        play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
                    } else {
                        play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
                    }
                    isPlaying = false;

                    player.reset();
                    if (!firstStart) {
                        stopUpdating = true;
                        songTimeUpdater.post(updateSongTime);
                    }

                    readyMediaPlayer(uri);
                }
            }
        }
    }

    private void selectAll() {
        mAdapter.selectAll();
        int count = mAdapter.getSelectionCount();

        if (count >= 2) {
            actionMode.getMenu().findItem(R.id.action_edit).setVisible(false);
            actionMode.getMenu().findItem(R.id.action_insert).setVisible(false);
            actionMode.getMenu().findItem(R.id.action_paste).setVisible(false);
            actionMode.getMenu().findItem(R.id.action_manually_set).setVisible(false);
        }

        actionMode.setTitle(String.valueOf(count));
        actionMode.invalidate();
    }

    private void copy() {
        int size = mAdapter.getSelectionCount();
        clipboard = new ItemData[size];

        List<Integer> selectedItemPositions = mAdapter.getSelectedItems();
        for (int i = 0; i < selectedItemPositions.size(); i++) {
            clipboard[i] = new ItemData(mAdapter.lyricData.get(selectedItemPositions.get(i)).getLyric(),
                    mAdapter.lyricData.get(selectedItemPositions.get(i)).getTimestamp());
        }

        Toast.makeText(this, "Copied the lyrics to the internal clipboard", Toast.LENGTH_SHORT).show();

        actionMode.finish();
        actionMode = null;
    }

    private void paste(final int mode) {
        longPressedPos = -1;
        longPressed = 0;

        for (int i = clipboard.length - 1; i >= 0; i--) {
            if (mode == 1) { /* Paste before */
                mAdapter.lyricData.add(mAdapter.getSelectedItems().get(0),
                        new ItemData(clipboard[i].getLyric(), clipboard[i].getTimestamp()));
            } else if (mode == 2) { /* Paste after */
                mAdapter.lyricData.add(mAdapter.getSelectedItems().get(0) + 1,
                        new ItemData(clipboard[i].getLyric(), clipboard[i].getTimestamp()));
            }
        }

        changedData = true;
        mAdapter.notifyDataSetChanged();

        if (mode == 1) {
            mRecyclerView.smoothScrollToPosition(mAdapter.getSelectedItems().get(0));
        } else if (mode == 2) {
            mRecyclerView.smoothScrollToPosition(mAdapter.getSelectedItems().get(0) + 1);
        }

        actionMode.finish();
        actionMode = null;
    }

    private void manuallySetTimestamp() {
        final int position = mAdapter.getSelectedItems().get(0);

        View view = this.getLayoutInflater().inflate(R.layout.manual_add_dialog, null);
        final EditText min = view.findViewById(R.id.manual_minutes_edittext);
        final EditText sec = view.findViewById(R.id.manual_seconds_edittext);
        final EditText mil = view.findViewById(R.id.manual_milliseconds_edittext);

        if (mAdapter.lyricData.get(position).getTimestamp() != null) {
            min.setText(mAdapter.lyricData.get(position).getTimestamp().substring(0, 2));
            sec.setText(mAdapter.lyricData.get(position).getTimestamp().substring(3, 5));
            mil.setText(mAdapter.lyricData.get(position).getTimestamp().substring(6, 8));
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Set", null)
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .create();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String minText = min.getText().toString().trim();
                        String secText = sec.getText().toString().trim();
                        String milText = mil.getText().toString().trim();

                        if (!minText.matches("^[0-9]+$")) {
                            min.setError("Invalid time");
                            return;
                        } else if (!secText.matches("^[0-9]+$")) {
                            sec.setError("Invalid time");
                            return;
                        } else if (!milText.matches("^[0-9]+$")) {
                            mil.setError("Invalid time");
                            return;
                        }

                        if (Integer.parseInt(secText) >= 60) {
                            sec.setError("Seconds must be less than 60");
                            return;
                        }

                        changedData = true;

                        if (milText.length() == 1) {
                            milText = "0" + milText;
                        }
                        if (secText.length() == 1) {
                            secText = "0" + secText;
                        }
                        if (minText.length() == 1) {
                            minText = "0" + minText;
                        }

                        String timestamp = String.format(Locale.getDefault(), "%s:%s.%s",
                                minText, secText, milText);
                        mAdapter.lyricData.get(position).setTimestamp(timestamp);

                        mAdapter.notifyItemChanged(position);

                        dialog.dismiss();
                    }
                });
            }
        });

        dialog.show();
        min.requestFocus();
    }

    private void remove() {
        String[] options = {"Delete timestamps only", "Delete both timestamps and the lyrics"};
        new AlertDialog.Builder(this)
                .setTitle("Choose what to delete")
                .setSingleChoiceItems(options, 0, null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int selectedOption = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                        List<Integer> selectedItemPositions =
                                mAdapter.getSelectedItems();
                        longPressedPos = -1;
                        longPressed = 0;
                        if (selectedOption == 0) { /* Delete timestamps only */
                            for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
                                mAdapter.lyricData.get(selectedItemPositions.get(i)).setTimestamp(null);
                            }
                        } else if (selectedOption == 1) { /* Delete both */
                            for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
                                mAdapter.lyricData.remove((int) selectedItemPositions.get(i));
                            }

                            if (mAdapter.lyricData.size() == 0) {
                                Toolbar toolbar = findViewById(R.id.Editortoolbar);
                                toolbar.getMenu().findItem(R.id.action_add).setVisible(true);
                            }
                        }

                        changedData = true;
                        mAdapter.notifyDataSetChanged();

                        actionMode.finish();
                        actionMode = null;
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void offsetTimestamps(int milli) {
        List<Integer> selectedItemPositions = mAdapter.getSelectedItems();

        for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
            String timestamp = mAdapter.lyricData.get(selectedItemPositions.get(i)).getTimestamp();
            if (timestamp == null) {
                timestamp = "00:00.00";
            }
            int time = timeToMilli(timestamp) + milli;
            if (time < 0)
                time = 0;
            else if (time > 5999999) /* 99:59:999 in milliseconds */ {
                time = 5999999;
            }
            timestamp = String.format(Locale.getDefault(), "%02d:%02d.%02d",
                    getMinutes(time), getSeconds(time), getMilli(time));
            mAdapter.lyricData.get(selectedItemPositions.get(i)).setTimestamp(timestamp);
        }

        mAdapter.notifyDataSetChanged();
    }

    private void edit_lyric_data(final int lyric_change) {
        final int position = mAdapter.getSelectedItems().get(0);

        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_layout, null);
        final EditText editText = view.findViewById(R.id.dialog_edittext);
        TextView textView = view.findViewById(R.id.dialog_prompt);

        String hint = null, positive_button_text = null;

        if (lyric_change == 1) {         /* Insert lyrics */

            textView.setText(getString(R.string.insert_lyrics_prompt));
            positive_button_text = getString(R.string.insert_lyrics_positive_button_text);
        } else if (lyric_change == 2) {  /* Edit selected lyric */

            textView.setText(getString(R.string.edit_prompt));
            editText.setText(mAdapter.lyricData.get(position).getLyric());

            positive_button_text = getString(R.string.edit_positive_button_text);
            hint = getString(R.string.edit_lyrics_hint);
        }

        if (hint != null) {
            editText.setHint(hint);
        } else {
            editText.setLines(10);
            editText.setSingleLine(false);
            editText.setHorizontalScrollBarEnabled(false);
            editText.setVerticalScrollBarEnabled(true);
            editText.setVerticalFadingEdgeEnabled(true);
            editText.setBackground(getDrawable(R.drawable.rounded_border));
            editText.setGravity(Gravity.CENTER);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(positive_button_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (lyric_change == 1) {         /* Insert lyrics */
                            String data = editText.getText().toString().trim();

                            String[] lyrics = data.split("\\n");
                            insert_lyrics(lyrics);

                        } else if (lyric_change == 2) {  /* Edit selected lyric */
                            changedData = true;

                            mAdapter.lyricData.get(position).setLyric(editText.getText().toString());
                            mAdapter.notifyItemChanged(position);

                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();


        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.show();
    }

    private void insert_lyrics(final String[] lyrics) {
        String[] options = {"Before the selected lyric item", "After the selected lyric item"};
        new AlertDialog.Builder(this)
                .setTitle("Choose where to insert")
                .setSingleChoiceItems(options, 0, null)
                .setPositiveButton("Insert", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int selectedOption = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                        int selectedItemPosition = mAdapter.getSelectedItems().get(0);
                        longPressedPos = -1;
                        longPressed = 0;

                        if (selectedOption == 0) { /* Before */
                            for (int i = lyrics.length - 1; i >= 0; i--) {
                                mAdapter.lyricData.add(selectedItemPosition,
                                        new ItemData(lyrics[i], null));
                            }
                        } else if (selectedOption == 1) { /* After */
                            for (int i = lyrics.length - 1; i >= 0; i--) {
                                mAdapter.lyricData.add(selectedItemPosition + 1,
                                        new ItemData(lyrics[i], null));
                            }
                        }

                        changedData = true;
                        mAdapter.notifyDataSetChanged();

                        if (selectedOption == 1) {
                            mRecyclerView.smoothScrollToPosition(mAdapter.getSelectedItems().get(0));
                        } else if (selectedOption == 2) {
                            mRecyclerView.smoothScrollToPosition(mAdapter.getSelectedItems().get(0) + 1);
                        }

                        actionMode.finish();
                        actionMode = null;
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void batch_edit_lyrics() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View view = inflater.inflate(R.layout.batch_edit_dialog, null);
        final TextView batchTimestamp = view.findViewById(R.id.batch_item_time);

        final Handler batchTimestampUpdater = new Handler();
        final int[] longPressed = {0};
        final boolean[] batchTimeNegative = {false};

        final Runnable updateBatchTimestamp = new Runnable() {
            @Override
            public void run() {
                String time = batchTimestamp.getText().toString();

                time = time.substring(1);

                long milli = timeToMilli(time);

                if (longPressed[0] == 1 && milli + 100 > 5999999) /* 99:59:999 in milliseconds */ {
                    milli = 5999999;

                    if (!batchTimeNegative[0]) {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "+%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                    } else {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "-%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                    }

                    longPressed[0] = 0;
                    return;
                }

                if (longPressed[0] == 1) {
                    if (!batchTimeNegative[0]) {
                        milli += 100;
                    } else {
                        milli -= 100;
                    }

                    if (batchTimeNegative[0]) {
                        batchTimeNegative[0] = !(milli <= 0);
                        if (milli < 0)
                            milli = -milli;
                    }
                } else if (longPressed[0] == -1) {
                    if (!batchTimeNegative[0]) {
                        milli -= 100;
                    } else {
                        milli += 100;
                    }

                    if (!batchTimeNegative[0]) {
                        batchTimeNegative[0] = milli < 0;
                        if (milli < 0)
                            milli = -milli;
                    }
                }

                if (!batchTimeNegative[0] || milli == 0) {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "+%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                } else {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "-%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                }

                if (longPressed[0] != 0)
                    timestampUpdater.postDelayed(this, 50);
            }
        };


        ImageButton increase = view.findViewById(R.id.batch_increase_time_button);
        if (isDarkTheme) {
            increase.setImageDrawable(getDrawable(R.drawable.ic_add_light));
        }
        increase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                longPressed[0] = 0;

                String time = batchTimestamp.getText().toString();

                time = time.substring(1);

                long milli = timeToMilli(time);

                if (milli + 100 > 5999999) /* 99:59:999 in milliseconds */ {
                    milli = 5999999;

                    if (!batchTimeNegative[0]) {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "+%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                    } else {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "-%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                    }

                    return;
                }

                if (!batchTimeNegative[0]) {
                    milli += 100;
                } else {
                    milli -= 100;
                }

                if (batchTimeNegative[0]) {
                    batchTimeNegative[0] = !(milli <= 0);
                    if (milli < 0)
                        milli = -milli;
                }

                if (!batchTimeNegative[0]) {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "+%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                } else {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "-%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                }
            }
        });

        increase.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (longPressed[0] != 1) {
                    longPressed[0] = 1;

                    batchTimestampUpdater.post(updateBatchTimestamp);
                }
                return false;
            }
        });

        ImageButton decrease = view.findViewById(R.id.batch_decrease_time_button);
        if (isDarkTheme) {
            decrease.setImageDrawable(getDrawable(R.drawable.ic_minus_light));
        }
        decrease.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                longPressed[0] = 0;

                String time = batchTimestamp.getText().toString();

                if (batchTimeNegative[0])
                    time = time.substring(1);

                long milli = timeToMilli(time);

                if (!batchTimeNegative[0]) {
                    milli -= 100;
                } else {
                    milli += 100;
                }

                if (!batchTimeNegative[0]) {
                    batchTimeNegative[0] = milli < 0;
                    if (milli < 0)
                        milli = -milli;
                }

                if (!batchTimeNegative[0] || milli == 0) {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "+%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                } else {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "-%02d:%02d.%02d", getMinutes(milli), getSeconds(milli), getMilli(milli)));
                }
            }
        });

        decrease.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (longPressed[0] != -1) {
                    longPressed[0] = -1;

                    batchTimestampUpdater.post(updateBatchTimestamp);
                }
                return false;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle("Batch Edit")
                .setPositiveButton("Adjust", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changedData = true;
                        longPressed[0] = 0;

                        String timestamp = batchTimestamp.getText().toString().substring(1);
                        if (batchTimeNegative[0]) {
                            offsetTimestamps(-timeToMilli(timestamp));
                        } else {
                            offsetTimestamps(timeToMilli(timestamp));
                        }

                        actionMode.finish();
                        flashCheck = true;
                        flasher.post(flash);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        longPressed[0] = 0;
                    }
                })
                .setCancelable(false)
                .create();

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_editoractivity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_done:
                if (longPressedPos != -1) {
                    longPressed = 0;
                    longPressedPos = -1;
                }
                Intent intent = new Intent(this, FinalizeActivity.class);
                intent.putExtra("lyricData", (ArrayList<ItemData>) mAdapter.lyricData);
                intent.putExtra("URI", uri);
                intent.putExtra("SONG METADATA", songMetaData);
                intent.putExtra("SONG FILE NAME", songFileName);
                intent.putExtra("LRC FILE NAME", lrcFileName);

                startActivity(intent);
                return true;
            case R.id.action_add:
                mAdapter.lyricData.add(new ItemData(" ", null));
                mAdapter.notifyItemChanged(0);
                Toolbar toolbar = findViewById(R.id.Editortoolbar);
                toolbar.getMenu().findItem(R.id.action_add).setVisible(false);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onBackPressed() {
        if (changedData) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("You'll lose your modified data if you go back. Are you sure you want to go back?")
                    .setPositiveButton("Go Back", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            reset();
                            EditorActivity.super.onBackPressed();
                        }
                    })
                    .setNegativeButton("Stay here", null)
                    .show();
        } else {
            reset();
            EditorActivity.super.onBackPressed();
        }
    }

    private void reset() {
        stopUpdating = true;
        flashCheck = false;
        longPressed = 0;
        longPressedPos = -1;
        try {
            player.stop();
        } catch (IllegalStateException e) { /* IDK why this gets thrown :shrug: */
            e.printStackTrace();
        }
        if (isDarkTheme) {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
        } else {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
        }
        isPlaying = false;
        playerPrepared = false;
        player.release();
    }

    private class stopFlash implements Runnable {
        int pos;

        stopFlash(int pos) {
            this.pos = pos;
        }

        @Override
        public void run() {
            mAdapter.stopFlash(this.pos);

            Handler waiter = new Handler();
            waiter.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mAdapter.getFlashingItems().size() == 0) {
                        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
                    }
                }
            }, 250);

        }
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextual_toolbar_editoractivity, menu);
            if (clipboard == null) {
                menu.findItem(R.id.action_paste).setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int optionMode;
            switch (item.getItemId()) {
                case R.id.action_delete:
                    remove();
                    return true;

                case R.id.action_edit:
                    optionMode = 2;
                    edit_lyric_data(optionMode);
                    mode.finish();
                    return true;

                case R.id.action_select_all:
                    selectAll();
                    return true;

                case R.id.action_manually_set:
                    manuallySetTimestamp();
                    return true;

                case R.id.action_copy:
                    copy();
                    return true;

                case R.id.action_paste_before:
                    optionMode = 1;
                    paste(optionMode);
                    return true;

                case R.id.action_paste_after:
                    optionMode = 2;
                    paste(optionMode);
                    return true;

                case R.id.action_insert:
                    optionMode = 1;
                    edit_lyric_data(optionMode);
                    return true;

                case R.id.action_batch_edit:
                    batch_edit_lyrics();
                    return true;

                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.clearSelections();
            actionMode = null;
        }
    }
}
