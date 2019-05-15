package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditorActivity extends AppCompatActivity implements LyricListAdapter.ItemClickListener,
        MediaPlayer.OnPreparedListener,
        SeekBar.OnSeekBarChangeListener,
        MediaPlayer.OnCompletionListener {

    private RecyclerView mRecyclerView;
    private LinearLayoutManager linearLayoutManager;
    private LyricListAdapter mAdapter;

    private boolean isPlaying = false;
    private boolean updateBusy = false;
    private boolean playerPrepared = false;
    private boolean changedData = false;

    private boolean isDarkTheme = false;

    private boolean startedTimeUpdate = false;

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

    private boolean mediaplayerIsCollapsed = false;

    private Timestamp seekTimestamp;

    private Handler timestampUpdater = new Handler();
    private int longPressed = 0;
    private int longPressedPos = -1;

    private TextView startText, endText;
    private TextView titleText;
    private ImageButton play_pause;

    private Handler flasher = new Handler();
    private Runnable flash = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) {
                flasher.postDelayed(this, 50);
                return;
            }

            int time = player.getCurrentPosition();
            int first = linearLayoutManager.findFirstVisibleItemPosition();
            int last = linearLayoutManager.findLastVisibleItemPosition();

            int pos = first;
            if (first == -1 || last == -1) {
                flasher.postDelayed(this, 50);
                return;
            }

            SparseBooleanArray s = mAdapter.getFlashingItems();
            while (pos <= last) {
                Timestamp timestamp;
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
                long currTime = timestamp.toMilliseconds();
                long diff = time - currTime;
                if (diff <= 100 && diff >= 0 && s.indexOfKey(pos) < 0) {
                    ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(true);
                    mAdapter.startFlash(pos);
                    flasher.postDelayed(new stopFlash(pos), 450);
                }
                pos++;
            }

            flasher.postDelayed(this, 30);
        }
    };

    private Runnable updateTimestamp = new Runnable() {
        @Override
        public void run() {
            if (longPressedPos == -1)
                return;

            Timestamp timestamp = mAdapter.lyricData.get(longPressedPos).getTimestamp();

            if (longPressed == 1) {
                timestamp.alterTimestamp(100);
            } else if (longPressed == -1) {
                timestamp.alterTimestamp(-100);
            }

            long time = timestamp.toMilliseconds();
            if (time == Constants.MAX_TIMESTAMP_VALUE || time == 0) {
                longPressed = 0;
            }

            mAdapter.notifyItemChanged(longPressedPos);

            if (longPressed != 0)
                timestampUpdater.postDelayed(this, 50);
            else {
                longPressedPos = -1;
            }
        }
    };

    private Runnable updateSongTime = new Runnable() {
        @Override
        public void run() {
            if (!updateBusy && isPlaying) {
                seekbar.setProgress(player.getCurrentPosition());
            }

            songTimeUpdater.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        String theme = preferences.getString("current_theme", "default_light");
        if (theme.equals("dark")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDark);
        } else if (theme.equals("darker")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDarker);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (isDarkTheme) {
            toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);
        }
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
            if (r.getErrorMsg() != null || !r.readLyrics()) {
                Toast.makeText(this, r.getErrorMsg(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            String[] lyrics = r.getLyrics();
            String[] timestamps = r.getTimestamps();
            lyricData = populateDataSet(lyrics, timestamps, false);

            songMetaData = r.getSongMetaData();

            lrcFileName = FileUtil.getFileName(this, intent.getData());
        } else {                        /* New LRC file or existing opened from the homepage */
            String[] lyrics = intent.getStringArrayExtra("LYRICS");
            String[] timestamps = intent.getStringArrayExtra("TIMESTAMPS");
            boolean newFile = intent.getBooleanExtra("NEW FILE", false);

            if (newFile) {
                lyricData = populateDataSet(lyrics, timestamps, true);
            } else {
                lyricData = populateDataSet(lyrics, timestamps, false);
            }

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
        }

        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        seekbar.setOnSeekBarChangeListener(this);

        flasher.post(flash);
    }

    private ArrayList<ItemData> populateDataSet(String[] lyrics, String[] timestamps, boolean insertStartAndEndTimes) {
        ArrayList<ItemData> lyricData = new ArrayList<>();

        if (insertStartAndEndTimes && !lyrics[0].trim().isEmpty())
            lyricData.add(new ItemData("", new Timestamp("00:00.00")));

        for (int i = 0, len = lyrics.length; i < len; i++) {
            try {
                lyricData.add(new ItemData(lyrics[i].trim(), new Timestamp(timestamps[i].trim())));
            } catch (ArrayIndexOutOfBoundsException | NullPointerException | IllegalArgumentException e) {
                lyricData.add(new ItemData(lyrics[i].trim(), null));
            }
        }

        if (insertStartAndEndTimes && !lyrics[lyrics.length - 1].trim().isEmpty())
            lyricData.add(new ItemData("", null));

        return lyricData;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isPlaying) {
            playPause(null);
        }
    }

    @Override
    public void onAddButtonClick(int position) {
        long pos;
        if (playerPrepared)
            pos = player.getCurrentPosition();
        else
            pos = 0;

        if (pos > Constants.MAX_TIMESTAMP_VALUE) {
            pos = Constants.MAX_TIMESTAMP_VALUE;
            Toast.makeText(this, "Timestamps larger than 99:59:999 are currently unsupported", Toast.LENGTH_SHORT).show();
        }

        Timestamp timestamp = mAdapter.lyricData.get(position).getTimestamp();
        if (timestamp == null) {
            mAdapter.lyricData.get(position).setTimestamp(new Timestamp(pos));
        } else {
            timestamp.setTime(pos);
        }

        changedData = true;

        mAdapter.notifyItemChanged(position);
        mRecyclerView.smoothScrollToPosition(position + 1);
    }

    @Override
    public void onPlayButtonClick(int position) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp timestamp = mAdapter.lyricData.get(position).getTimestamp();
        player.seekTo((int) timestamp.toMilliseconds());

        if (!startedTimeUpdate) {
            startedTimeUpdate = true;
            timestampUpdater.post(updateSongTime);
        }

        if (!isPlaying) {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
            player.start();
            isPlaying = true;
        }

        seekbar.setProgress(player.getCurrentPosition());
    }

    @Override
    public void onIncreaseTimeClick(int position) {
        longPressed = 0;
        longPressedPos = -1;

        Timestamp timestamp = mAdapter.lyricData.get(position).getTimestamp();
        timestamp.alterTimestamp(100);
        mAdapter.notifyItemChanged(position);

        changedData = true;

        if (playerPrepared) {
            player.seekTo((int) timestamp.toMilliseconds());

            if (!startedTimeUpdate) {
                startedTimeUpdate = true;
                timestampUpdater.post(updateSongTime);
            }

            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }

            player.start();
            isPlaying = true;
            seekbar.setProgress(player.getCurrentPosition());
        }
    }

    @Override
    public void onDecreaseTimeClick(int position) {
        longPressed = 0;
        longPressedPos = -1;

        Timestamp timestamp = mAdapter.lyricData.get(position).getTimestamp();
        timestamp.alterTimestamp(-100);
        mAdapter.notifyItemChanged(position);

        changedData = true;

        if (playerPrepared) {
            player.seekTo((int) timestamp.toMilliseconds());

            if (!startedTimeUpdate) {
                startedTimeUpdate = true;
                timestampUpdater.post(updateSongTime);
            }

            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }

            player.start();
            isPlaying = true;
            seekbar.setProgress(player.getCurrentPosition());
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

    private void collapseOrExpandMediaplayer() {
        if (!mediaplayerIsCollapsed) {
            titleText.setVisibility(View.GONE);
            LinearLayout mediaControlsExpanded = findViewById(R.id.mediaControlsExpanded);
            mediaControlsExpanded.setVisibility(View.GONE);
            seekbar.setVisibility(View.GONE);
            LinearLayout mediaControlsCollapsed = findViewById(R.id.mediaControlsCollapsed);
            mediaControlsCollapsed.setVisibility(View.VISIBLE);
            play_pause = findViewById(R.id.play_pause_collapsed);
            RelativeLayout mediaplayerMid = findViewById(R.id.mediaplayerMid);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mediaplayerMid.getLayoutParams();
            params.topMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 15, getResources()
                            .getDisplayMetrics());

            mediaplayerIsCollapsed = true;
        } else {
            titleText.setVisibility(View.VISIBLE);
            LinearLayout mediaControlsExpanded = findViewById(R.id.mediaControlsExpanded);
            mediaControlsExpanded.setVisibility(View.VISIBLE);
            seekbar.setVisibility(View.VISIBLE);
            LinearLayout mediaControlsCollapsed = findViewById(R.id.mediaControlsCollapsed);
            mediaControlsCollapsed.setVisibility(View.GONE);
            play_pause = findViewById(R.id.play_pause);
            RelativeLayout mediaplayerMid = findViewById(R.id.mediaplayerMid);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mediaplayerMid.getLayoutParams();
            params.topMargin = 0;

            mediaplayerIsCollapsed = false;
        }

        if (isPlaying) {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }
        } else {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
            }
        }
    }

    private void readyMediaPlayer(Uri songUri) {
        try {
            player.setDataSource(this, songUri);
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
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

    public void playPause(View view) {
        if (!playerPrepared) {
            if (view != null) {
                Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!startedTimeUpdate) {
            startedTimeUpdate = true;
            timestampUpdater.post(updateSongTime);
        }

        if (isPlaying) {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
            }

            player.pause();
        } else {
            if (isDarkTheme) {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
            } else {
                play_pause.setImageDrawable(getDrawable(R.drawable.ic_pause));
            }

            player.start();
        }
        isPlaying = !isPlaying;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playerPrepared = true;
        startText.setText(getString(R.string.default_timetext));
        seekTimestamp = new Timestamp(0, 0, 0);
        int duration = player.getDuration();
        Timestamp endTime = new Timestamp(duration);
        endTime.setMilliseconds(0);
        endText.setText(String.format(Locale.getDefault(), "%02d:%02d", endTime.getMinutes(), endTime.getSeconds()));
        seekbar.setMax(duration);
        seekbar.setProgress(player.getCurrentPosition());
        songFileName = FileUtil.getFileName(this, uri);
    }

    public void rewind5(View view) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        player.seekTo(player.getCurrentPosition() - 5 * 1000);
        seekbar.setProgress(player.getCurrentPosition());
    }

    public void forward5(View view) {
        if (!playerPrepared) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        player.seekTo(player.getCurrentPosition() + 5 * 1000);
        seekbar.setProgress(player.getCurrentPosition());
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekTimestamp != null) {
            seekTimestamp.setTime(progress);
            seekTimestamp.setMilliseconds(0);
            startText.setText(String.format(Locale.getDefault(), "%02d:%02d", seekTimestamp.getMinutes(), seekTimestamp.getSeconds()));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        updateBusy = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekTimestamp != null)
            player.seekTo((int) seekTimestamp.toMilliseconds());
        updateBusy = false;
        seekbar.setProgress(player.getCurrentPosition());
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (isDarkTheme) {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
        } else {
            play_pause.setImageDrawable(getDrawable(R.drawable.ic_play));
        }

        isPlaying = false;
    }

    public void selectSong(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");

        try {
            startActivityForResult(intent, Constants.FILE_REQUEST);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open the system song picker dialog; Are you sure you're running Android Kitkat or up?", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == Constants.FILE_REQUEST && resultCode == Activity.RESULT_OK) {
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
                    seekbar.setProgress(player.getCurrentPosition());

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
            min.setText(String.format(Locale.getDefault(), "%02d", mAdapter.lyricData.get(position).getTimestamp().getMinutes()));
            sec.setText(String.format(Locale.getDefault(), "%02d", mAdapter.lyricData.get(position).getTimestamp().getSeconds()));
            mil.setText(String.format(Locale.getDefault(), "%02d", mAdapter.lyricData.get(position).getTimestamp().getMilliseconds() / 10));
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

                        if (minText.length() == 1) {
                            minText = "0" + minText;
                        }
                        if (secText.length() == 1) {
                            secText = "0" + secText;
                        }
                        if (milText.length() == 1) {
                            milText = "0" + milText;
                        }

                        milText = milText + "0";

                        Timestamp timestamp = mAdapter.lyricData.get(position).getTimestamp();
                        if (timestamp == null) {
                            timestamp = new Timestamp(Long.parseLong(minText),
                                    Long.parseLong(secText),
                                    Long.parseLong(milText));
                            mAdapter.lyricData.get(position).setTimestamp(timestamp);
                        } else {
                            timestamp.setTime(Long.parseLong(minText),
                                    Long.parseLong(secText),
                                    Long.parseLong(milText));
                        }

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
                                Toolbar toolbar = findViewById(R.id.toolbar);
                                toolbar.getMenu().findItem(R.id.action_add).setVisible(true);
                            }
                        }

                        changedData = true;
                        mAdapter.notifyDataSetChanged();

                        try {
                            actionMode.finish();
                        } catch (NullPointerException e) { // Can occur when the user quickly double taps a menu item
                            e.printStackTrace();
                        }
                        actionMode = null;
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void offsetTimestamps(long milli) {
        List<Integer> selectedItemPositions = mAdapter.getSelectedItems();

        for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
            Timestamp timestamp = mAdapter.lyricData.get(selectedItemPositions.get(i)).getTimestamp();
            if (timestamp == null) {
                timestamp = new Timestamp("00:00.00");
                mAdapter.lyricData.get(selectedItemPositions.get(i)).setTimestamp(timestamp);
            }

            timestamp.alterTimestamp(milli);
        }

        mAdapter.notifyDataSetChanged();
    }

    private void edit_lyric_data(final int lyric_change) {
        final int position;
        try {
            position = mAdapter.getSelectedItems().get(0);
        } catch (IndexOutOfBoundsException e) { // Gets thrown when the user quickly double taps the edit menu button
            e.printStackTrace();
            return;
        }

        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.edit_dialog, null);
        final EditText editText = view.findViewById(R.id.dialog_edittext);
        TextView textView = view.findViewById(R.id.dialog_prompt);

        String hint = null, positive_button_text = null;

        if (lyric_change == 1) {         /* Insert lyrics */
            textView.setText(getString(R.string.insert_lyrics_prompt));
            positive_button_text = getString(R.string.insert_lyrics_positive_button_text);

            editText.setLines(10);
            editText.setSingleLine(false);
            editText.setHorizontalScrollBarEnabled(false);
            editText.setVerticalScrollBarEnabled(true);
            editText.setVerticalFadingEdgeEnabled(true);
            if (isDarkTheme) {
                editText.setBackground(getDrawable(R.drawable.rounded_border_light));
            } else {
                editText.setBackground(getDrawable(R.drawable.rounded_border));
            }
            editText.setGravity(Gravity.CENTER);

        } else if (lyric_change == 2) {  /* Edit selected lyric */

            textView.setText(getString(R.string.edit_prompt));
            editText.setText(mAdapter.lyricData.get(position).getLyric());

            positive_button_text = getString(R.string.edit_positive_button_text);
            hint = getString(R.string.edit_lyrics_hint);
            editText.setHint(hint);
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

        final Timestamp timestamp = new Timestamp("00:00.00");

        final Handler batchTimestampUpdater = new Handler();
        final int[] longPressed = {0};
        final boolean[] batchTimeNegative = {false}; // Have to use an array because JAVA

        final Runnable updateBatchTimestamp = new Runnable() {
            @Override
            public void run() {
                long milli = timestamp.toMilliseconds();

                if (longPressed[0] != 0) {
                    if (longPressed[0] == 1) {
                        if (batchTimeNegative[0])
                            timestamp.alterTimestamp(-100);
                        else
                            timestamp.alterTimestamp(100);

                        if (batchTimeNegative[0] && timestamp.toMilliseconds() <= 0) {
                            batchTimeNegative[0] = false;
                        }
                    } else {
                        if (!batchTimeNegative[0] && timestamp.toMilliseconds() - 100 < 0)
                            batchTimeNegative[0] = true;

                        if (batchTimeNegative[0])
                            timestamp.alterTimestamp(100);
                        else
                            timestamp.alterTimestamp(-100);
                    }

                    if (!batchTimeNegative[0]) {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "+%s", timestamp.toString()));
                    } else {
                        batchTimestamp.setText(
                                String.format(Locale.getDefault(), "-%s", timestamp.toString()));
                    }

                    timestampUpdater.postDelayed(this, 50);
                }
            }
        };


        ImageButton increase = view.findViewById(R.id.batch_increase_time_button);
        if (isDarkTheme) {
            increase.setImageDrawable(getDrawable(R.drawable.ic_add_light));
        }

        increase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long milli = timestamp.toMilliseconds();

                if (batchTimeNegative[0])
                    timestamp.alterTimestamp(-100);
                else
                    timestamp.alterTimestamp(100);

                if (batchTimeNegative[0] && timestamp.toMilliseconds() <= 0) {
                    batchTimeNegative[0] = false;
                }

                if (!batchTimeNegative[0]) {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "+%s", timestamp.toString()));
                } else {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "-%s", timestamp.toString()));
                }

                longPressed[0] = 0;
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
                long milli = timestamp.toMilliseconds();

                if (!batchTimeNegative[0] && timestamp.toMilliseconds() - 100 < 0)
                    batchTimeNegative[0] = true;

                if (batchTimeNegative[0])
                    timestamp.alterTimestamp(100);
                else
                    timestamp.alterTimestamp(-100);

                if (!batchTimeNegative[0]) {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "+%s", timestamp.toString()));
                } else {
                    batchTimestamp.setText(
                            String.format(Locale.getDefault(), "-%s", timestamp.toString()));
                }

                longPressed[0] = 0;
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

                        if (batchTimeNegative[0]) {
                            offsetTimestamps(-timestamp.toMilliseconds());
                        } else {
                            offsetTimestamps(timestamp.toMilliseconds());
                        }

                        actionMode.finish();
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
                Toolbar toolbar = findViewById(R.id.toolbar);
                toolbar.getMenu().findItem(R.id.action_add).setVisible(false);
                return true;
            case R.id.action_collapse_or_expand:
                collapseOrExpandMediaplayer();
                if (mediaplayerIsCollapsed) {
                    item.setIcon(getDrawable(R.drawable.ic_expand));
                } else {
                    item.setIcon(getDrawable(R.drawable.ic_collapse));
                }
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
        flasher.removeCallbacks(flash);
        songTimeUpdater.removeCallbacks(updateSongTime);

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
