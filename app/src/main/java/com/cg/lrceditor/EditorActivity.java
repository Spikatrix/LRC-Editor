package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditorActivity extends AppCompatActivity implements LyricListAdapter.ItemClickListener,
		MediaPlayer.OnPreparedListener,
		SeekBar.OnSeekBarChangeListener,
		MediaPlayer.OnCompletionListener {

	private RecyclerView recyclerView;
	private LinearLayoutManager linearLayoutManager;
	private LyricListAdapter adapter;

	private SwipeRefreshLayout swipeRefreshLayout;

	private boolean isPlaying = false;
	private boolean updateBusy = false;
	private boolean playerPrepared = false;
	private boolean changedData = false;
	private boolean startedTimeUpdate = false;
	private boolean mediaplayerIsCollapsed = false;

	private boolean isDarkTheme = false;

	private String lrcFileName = null;
	private String lrcFilePath = null;
	private Uri songUri = null;
	private String songFileName = null;

	private Metadata metadata = null;

	private LyricItem[] clipboard = null;

	private MenuItem playbackOptions;

	private ActionModeCallback actionModeCallback;
	private ActionMode actionMode;

	private MediaPlayer player;
	private MediaSession mediaSession;
	private float currentPlayerSpeed;
	private float currentPlayerPitch;
	private int timestampAdjustStep;

	private Handler songTimeUpdater = new Handler();
	private SeekBar seekbar;
	private Timestamp seekTimestamp;
	private Handler timestampUpdater = new Handler();

	private int longPressed = 0;
	private int longPressedPos = -1;

	private TextView startText, endText;
	private TextView titleText;
	private ImageButton playPause;

	/* Takes care of flashing lyric items as the player reaches the corresponding timestamp */
	private Handler flasher = new Handler();
	private Runnable flash = new Runnable() {
		@Override
		public void run() {
			if (!isPlaying) {
				flasher.postDelayed(this, 35);
				return;
			}

			int first = linearLayoutManager.findFirstVisibleItemPosition();
			int last = linearLayoutManager.findLastVisibleItemPosition();

			if (first == -1 || last == -1) {
				flasher.postDelayed(this, 35);
				return;
			}

			SparseBooleanArray s = adapter.getFlashingItems();
			Timestamp timestamp;
			for (int pos = first; pos <= last; pos++) {
				try {
					timestamp = adapter.lyricData.get(pos).getTimestamp();
				} catch (IndexOutOfBoundsException ignored) {
					continue;
				}
				if (timestamp == null) {
					continue;
				}

				int time = player.getCurrentPosition();
				long currTime = timestamp.toMilliseconds();
				long diff = time - currTime;
				if (diff <= 100 && diff >= 0 && s.indexOfKey(pos) < 0) {
					((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(true);
					adapter.startFlash(pos);
					flasher.postDelayed(new stopFlash(pos), 450);
				}
			}

			flasher.postDelayed(this, 35);
		}
	};

	/* Fast coarse timestamp adjustment by long pressing on the '+' or '-' button of any timestamp */
	private Runnable updateTimestamp = new Runnable() {
		@Override
		public void run() {
			/* longPressedPos is the position of the item whose timestamp adjust button was pressed (-1 to stop) */
			/* longPressed is 1 when the '+' button is pressed and -1 when the '-' button is pressed (0 if neither) */

			if (longPressedPos == -1)
				return;

			Timestamp timestamp = adapter.lyricData.get(longPressedPos).getTimestamp();

			if (longPressed == 1) {
				timestamp.alterTimestamp(timestampAdjustStep);
			} else if (longPressed == -1) {
				timestamp.alterTimestamp(-timestampAdjustStep);
			}

			long time = timestamp.toMilliseconds();
			if (time == Constants.MAX_TIMESTAMP_VALUE || time == 0) {
				longPressed = 0;
			}

			adapter.notifyItemChanged(longPressedPos);

			if (longPressed != 0)
				timestampUpdater.postDelayed(this, 50);
			else {
				longPressedPos = -1;
			}
		}
	};

	/* Updates the seekbar when a song is played */
	private Runnable updateSongTime = new Runnable() {
		@Override
		public void run() {
			if (!updateBusy && isPlaying) {
				seekbar.setProgress(player.getCurrentPosition());
			}

			songTimeUpdater.postDelayed(this, 100);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SharedPreferences preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString(Constants.THEME_PREFERENCE, "light");
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
			/* Dark toolbar popups for dark themes */
			toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);
		}
		setSupportActionBar(toolbar);

		try {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

		timestampAdjustStep = preferences.getInt(Constants.TIMESTAMP_STEP_AMOUNT_PREFERENCE, 100);

		swipeRefreshLayout = findViewById(R.id.swiperefresh);
		swipeRefreshLayout.setEnabled(false);

		recyclerView = findViewById(R.id.recyclerview);
		((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
		linearLayoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(linearLayoutManager);

		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
				DividerItemDecoration.VERTICAL);
		recyclerView.addItemDecoration(dividerItemDecoration);

		final Intent intent = getIntent();
		Uri intentData = intent.getData();

		if (intentData != null) {
			/* LRC File opened externally */

			final LyricListAdapter.ItemClickListener clickListener = this;
			final Context ctx = this;

			final LyricReader r = new LyricReader(intentData, this);
			new Thread(() -> {
				runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));

				if (r.getErrorMsg() != null || !r.readLyrics()) {
					runOnUiThread(() -> {
						Toast.makeText(ctx, r.getErrorMsg(), Toast.LENGTH_LONG).show();
						swipeRefreshLayout.setRefreshing(false);
						finish();
					});
					return;
				}

				runOnUiThread(() -> {
					ArrayList<LyricItem> lyricData = r.getLyricData();

					metadata = r.getMetadata();
					lrcFileName = FileUtil.getFileName(ctx, intent.getData());

					adapter = new LyricListAdapter(ctx, lyricData, isDarkTheme);
					adapter.setClickListener(clickListener);
					recyclerView.setAdapter(adapter);

					swipeRefreshLayout.setRefreshing(false);
				});
			}).start();
		} else {
			/* New LRC file or existing opened from the homepage */

			String[] lyrics = intent.getStringArrayExtra(IntentSharedStrings.LYRICS);
			ArrayList<LyricItem> lyricData = (ArrayList<LyricItem>) intent.getSerializableExtra(IntentSharedStrings.LYRIC_DATA);

			if (lyrics != null) {
				// CreateActivity started EditorActivity
				lyricData = createLyricData(lyrics);
			}

			metadata = (Metadata) intent.getSerializableExtra(IntentSharedStrings.METADATA);
			lrcFileName = intent.getStringExtra(IntentSharedStrings.LRC_FILE_NAME);
			lrcFilePath = intent.getStringExtra(IntentSharedStrings.LRC_FILE_PATH);

			adapter = new LyricListAdapter(this, lyricData, isDarkTheme);
			adapter.setClickListener(this);
			recyclerView.setAdapter(adapter);
		}

		actionModeCallback = new ActionModeCallback();

		player = new MediaPlayer();
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		currentPlayerSpeed = currentPlayerPitch = 1.0f;
		mediaSession = null;

		seekbar = findViewById(R.id.seekbar);
		startText = findViewById(R.id.start_time_text);
		endText = findViewById(R.id.end_time_text);
		playPause = findViewById(R.id.play_pause);
		titleText = findViewById(R.id.player_title_text);

		titleText.requestFocus();

		if (isDarkTheme) {
			playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
		}

		player.setOnPreparedListener(this);
		player.setOnCompletionListener(this);
		seekbar.setOnSeekBarChangeListener(this);

		flasher.post(flash);
	}

	private ArrayList<LyricItem> createLyricData(String[] lyrics) {
		ArrayList<LyricItem> lyricData = new ArrayList<>();

		if (!lyrics[0].trim().isEmpty()) {
			lyricData.add(new LyricItem("", new Timestamp("00:00.00")));
		}

		for (String lyric : lyrics) {
			lyricData.add(new LyricItem(lyric, null));
		}

		if (!lyrics[lyrics.length - 1].trim().isEmpty()) {
			lyricData.add(new LyricItem("", null));
		}

		return lyricData;
	}

	private void setUpMediaSession() {
		if (mediaSession == null) {
			mediaSession = new MediaSession(this, "MediaSession");
			mediaSession.setCallback(new MediaSession.Callback() {
				@Override
				public void onPlay() {
					if (!playerPrepared) {
						Toast.makeText(getApplicationContext(), getString(R.string.player_not_ready_message), Toast.LENGTH_SHORT).show();
					}

					playPause(null);
					super.onPlay();
				}

				@Override
				public void onPause() {
					playPause(null);
					super.onPause();
				}

				@Override
				public void onFastForward() {
					forward5(null);
					super.onFastForward();
				}

				@Override
				public void onRewind() {
					rewind5(null);
					super.onRewind();
				}
			});

			mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
					MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

			PlaybackState state = new PlaybackState.Builder()
					.setActions(PlaybackState.ACTION_PLAY)
					.setState(PlaybackState.STATE_STOPPED, 0, currentPlayerSpeed)
					.build();

			mediaSession.setPlaybackState(state);
		}

		mediaSession.setActive(true);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		try {
			outState.putSerializable("LYRIC DATA", (ArrayList<LyricItem>) adapter.lyricData);
		} catch (NullPointerException e) { // Not sure why I get crash reports on this. `adapter.lyricData` should not be `null`...
			e.printStackTrace();
		}

		try {
			outState.putParcelable("SONG URI", songUri);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		ArrayList<LyricItem> lyricData = (ArrayList<LyricItem>) savedInstanceState.getSerializable("LYRIC DATA");
		if (lyricData != null) {
			adapter = new LyricListAdapter(this, lyricData, isDarkTheme);
			adapter.setClickListener(this);
			adapter.clearSelections();
			recyclerView.setAdapter(adapter);
		}

		songUri = savedInstanceState.getParcelable("SONG URI");
		if (songUri != null) {
			readyUpMediaPlayer(songUri);
		}

		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mediaSession.setActive(false);
		if (isPlaying) {
			playPause(null);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		setUpMediaSession();
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
			Toast.makeText(this, getString(R.string.large_timestamp_unsupported_message), Toast.LENGTH_SHORT).show();
		}

		recyclerView.smoothScrollToPosition(position + 1);

		Timestamp timestamp = adapter.lyricData.get(position).getTimestamp();
		if (timestamp == null) {
			adapter.lyricData.get(position).setTimestamp(new Timestamp(pos));
		} else {
			timestamp.setTime(pos);
		}

		changedData = true;

		adapter.notifyItemChanged(position);
	}

	@Override
	public void onPlayButtonClick(int position) {
		if (!playerPrepared) {
			Toast.makeText(this, getString(R.string.player_not_ready_message), Toast.LENGTH_SHORT).show();
			return;
		}

		Timestamp timestamp = adapter.lyricData.get(position).getTimestamp();
		if (timestamp == null) {
			Toast.makeText(this, getString(R.string.no_timestamp_set_message), Toast.LENGTH_SHORT).show();
			return;
		}
		player.seekTo((int) timestamp.toMilliseconds());

		if (!startedTimeUpdate) {
			startedTimeUpdate = true;
			timestampUpdater.post(updateSongTime);
		}

		if (!isPlaying) {
			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
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

		Timestamp timestamp = adapter.lyricData.get(position).getTimestamp();
		timestamp.alterTimestamp(timestampAdjustStep);
		adapter.notifyItemChanged(position);

		changedData = true;

		if (playerPrepared) {
			player.seekTo((int) timestamp.toMilliseconds());

			if (!startedTimeUpdate) {
				startedTimeUpdate = true;
				timestampUpdater.post(updateSongTime);
			}

			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
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

		Timestamp timestamp = adapter.lyricData.get(position).getTimestamp();
		timestamp.alterTimestamp(-timestampAdjustStep);
		adapter.notifyItemChanged(position);

		changedData = true;

		if (playerPrepared) {
			player.seekTo((int) timestamp.toMilliseconds());

			if (!startedTimeUpdate) {
				startedTimeUpdate = true;
				timestampUpdater.post(updateSongTime);
			}

			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
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
		adapter.toggleSelection(position);

		checkActionModeItems();
	}

	private void checkActionModeItems() {
		int count = adapter.getSelectionCount();

		if (count == 0) {
			if (actionMode != null) {
				actionMode.finish();
			}
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
			LinearLayout mediaControlsExpanded = findViewById(R.id.media_controls_expanded);
			mediaControlsExpanded.setVisibility(View.GONE);
			seekbar.setVisibility(View.GONE);
			LinearLayout mediaControlsCollapsed = findViewById(R.id.media_controls_collapsed);
			mediaControlsCollapsed.setVisibility(View.VISIBLE);
			playPause = findViewById(R.id.play_pause_collapsed);
			RelativeLayout mediaplayerMid = findViewById(R.id.mediaplayer_mid);
			ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mediaplayerMid.getLayoutParams();
			params.topMargin = (int) TypedValue.applyDimension(
					TypedValue.COMPLEX_UNIT_DIP, 12, getResources()
							.getDisplayMetrics());

			mediaplayerIsCollapsed = true;
		} else {
			titleText.setVisibility(View.VISIBLE);
			LinearLayout mediaControlsExpanded = findViewById(R.id.media_controls_expanded);
			mediaControlsExpanded.setVisibility(View.VISIBLE);
			seekbar.setVisibility(View.VISIBLE);
			LinearLayout mediaControlsCollapsed = findViewById(R.id.media_controls_collapsed);
			mediaControlsCollapsed.setVisibility(View.GONE);
			playPause = findViewById(R.id.play_pause);
			RelativeLayout mediaplayerMid = findViewById(R.id.mediaplayer_mid);
			ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mediaplayerMid.getLayoutParams();
			params.topMargin = 0;
			titleText.setSelected(true);

			mediaplayerIsCollapsed = false;
		}

		if (isPlaying) {
			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
			}
		} else {
			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_play));
			}
		}
	}

	private void readyUpMediaPlayer(final Uri songUriArgument) {
		titleText.setText(getString(R.string.setting_up_player_message));
		new Thread(() -> {
			try {
				player.setDataSource(getApplicationContext(), songUriArgument);
			} catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
				runOnUiThread(() -> mediaPlayerSetUpFailed(e));
				return;
			}

			runOnUiThread(() -> {
				playerPrepared = false;
				songUri = songUriArgument;

				titleText.setText(getString(R.string.preparing_the_player_message));
				try {
					player.prepareAsync();
				} catch (IllegalStateException e) {
					// Got a crash report
					mediaPlayerSetUpFailed(e);
				}
			});

		}).start();
	}

	private void mediaPlayerSetUpFailed(Exception e) {
		Toast.makeText(getApplicationContext(), "Whoops " + e.getMessage(), Toast.LENGTH_LONG).show();
		titleText.setText(getString(R.string.tap_to_select_song_prompt));
		e.printStackTrace();
	}

	public void playPause(View view) {
		if (!playerPrepared) {
			if (view != null) {
				//Toast.makeText(this, getString(R.string.player_not_ready_message), Toast.LENGTH_SHORT).show();
				selectSong(view); // Many people could not open the song picker the usual way for some odd reason
			}
			return;
		}

		if (!startedTimeUpdate) {
			startedTimeUpdate = true;
			timestampUpdater.post(updateSongTime);
		}

		if (isPlaying) {
			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_play));
			}

			player.pause();
		} else {
			if (isDarkTheme) {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause_light));
			} else {
				playPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
			}

			player.start();
		}
		isPlaying = !isPlaying;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		int duration = player.getDuration();
		Timestamp endTime;
		try {
			endTime = new Timestamp(duration);
		} catch (IllegalArgumentException e) { // Negative timestamp; live content?
			titleText.setText(getString(R.string.tap_to_select_song_prompt));
			Toast.makeText(this, getString(R.string.failed_to_get_duration_message), Toast.LENGTH_LONG).show();
			return;
		}
		songFileName = FileUtil.getFileName(this, songUri);
		setPlayerTitle(songFileName);
		startText.setText("00:00");
		seekTimestamp = new Timestamp(0, 0, 0);
		endTime.setMilliseconds(0);
		endText.setText(String.format(Locale.getDefault(), "%02d:%02d", endTime.getMinutes(), endTime.getSeconds()));
		seekbar.setMax(duration);
		seekbar.setProgress(player.getCurrentPosition());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Because PlaybackParams is only supported on Android 6.0 and above
			currentPlayerPitch = currentPlayerSpeed = 1.0f;

			if (playbackOptions != null) {
				playbackOptions.setVisible(true);
			} else {
				// Can't repro myself, but got a couple of crash reports on this
				// Possible race condition? Attempt it again after waiting for 1 second
				new Handler().postDelayed(() -> {
					if (playbackOptions != null) {
						playbackOptions.setVisible(true);
					} else {
						Toast.makeText(EditorActivity.this, getString(R.string.media_playback_options_init_failed), Toast.LENGTH_SHORT).show();
					}
				}, 1000);
			}
		}

		playerPrepared = true;
	}

	private void setPlayerTitle(String fallBackSongTitle) {
		MediaMetadataRetriever mmr = new MediaMetadataRetriever();
		try {
			mmr.setDataSource(this, songUri);
			String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
			if (title != null && !title.trim().isEmpty()) {
				titleText.setText(title);
				return;
			}
		} catch (RuntimeException ignored) {
		}

		if (fallBackSongTitle != null && !fallBackSongTitle.isEmpty()) {
			titleText.setText(fallBackSongTitle);
		} else {
			titleText.setText(getString(R.string.title_error_text));
		}
	}

	public void rewind5(View view) {
		if (!playerPrepared) {
			Toast.makeText(this, getString(R.string.player_not_ready_message), Toast.LENGTH_SHORT).show();
			return;
		}

		player.seekTo(player.getCurrentPosition() - 5 * 1000);
		seekbar.setProgress(player.getCurrentPosition());
	}

	public void forward5(View view) {
		if (!playerPrepared) {
			Toast.makeText(this, getString(R.string.player_not_ready_message), Toast.LENGTH_SHORT).show();
			return;
		}

		player.seekTo(player.getCurrentPosition() + 5 * 1000);
		seekbar.setProgress(player.getCurrentPosition());
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (seekTimestamp != null) {
			seekTimestamp.setTime(progress);
			startText.setText(String.format(Locale.getDefault(), "%02d:%02d", seekTimestamp.getMinutes(), seekTimestamp.getSeconds()));
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		updateBusy = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		if (seekTimestamp != null) {
			player.seekTo((int) seekTimestamp.toMilliseconds());
		}
		updateBusy = false;
		seekbar.setProgress(player.getCurrentPosition());
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		if (isDarkTheme) {
			playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
		} else {
			playPause.setImageDrawable(getDrawable(R.drawable.ic_play));
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
			Toast.makeText(this, getString(R.string.failed_to_open_file_picker_message), Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		super.onActivityResult(requestCode, resultCode, resultData);
		if (requestCode == Constants.FILE_REQUEST && resultCode == Activity.RESULT_OK) {
			if (resultData != null) {
				Uri uri = resultData.getData();
				if (uri != null) {
					playerPrepared = false;

					if (isDarkTheme) {
						playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
					} else {
						playPause.setImageDrawable(getDrawable(R.drawable.ic_play));
					}
					isPlaying = false;

					player.reset();
					seekbar.setProgress(player.getCurrentPosition());

					readyUpMediaPlayer(uri);
				}
			}
		}
	}

	private void selectAll() {
		adapter.selectAll();
		int count = adapter.getSelectionCount();

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
		int size = adapter.getSelectionCount();
		clipboard = new LyricItem[size];

		List<Integer> selectedItemPositions = adapter.getSelectedItemIndices();
		for (int i = 0; i < selectedItemPositions.size(); i++) {
			Timestamp timestamp = adapter.lyricData.get(selectedItemPositions.get(i)).getTimestamp();
			if (timestamp != null) {
				clipboard[i] = new LyricItem(adapter.lyricData.get(selectedItemPositions.get(i)).getLyric(),
						new Timestamp(timestamp));
			} else {
				clipboard[i] = new LyricItem(adapter.lyricData.get(selectedItemPositions.get(i)).getLyric(),
						null);
			}
		}

		Toast.makeText(this, getString(R.string.copied_lyrics_message), Toast.LENGTH_SHORT).show();

		if (actionMode != null) {
			actionMode.finish();
		}
		actionMode = null;
	}

	private void paste(final int mode) {
		longPressedPos = -1;
		longPressed = 0;

		int selectedItemPosition = adapter.getSelectedItemIndices().get(0);
		for (int i = clipboard.length - 1; i >= 0; i--) {
			Timestamp timestamp = clipboard[i].getTimestamp();
			if (mode == -1) {        /* Paste before */
				if (timestamp != null) {
					adapter.lyricData.add(selectedItemPosition,
							new LyricItem(clipboard[i].getLyric(), new Timestamp(timestamp)));
				} else {
					adapter.lyricData.add(selectedItemPosition,
							new LyricItem(clipboard[i].getLyric(), null));
				}
				adapter.notifyItemInserted(selectedItemPosition);
			} else if (mode == +1) { /* Paste after */
				if (timestamp != null) {
					adapter.lyricData.add(selectedItemPosition + 1,
							new LyricItem(clipboard[i].getLyric(), new Timestamp(timestamp)));
				} else {
					adapter.lyricData.add(selectedItemPosition + 1,
							new LyricItem(clipboard[i].getLyric(), null));
				}
				adapter.notifyItemInserted(selectedItemPosition + 1);

			}
		}

		changedData = true;

		if (mode == -1) {
			recyclerView.smoothScrollToPosition(selectedItemPosition);
		} else if (mode == +1) {
			recyclerView.smoothScrollToPosition(selectedItemPosition + 1);
		}

		if (actionMode != null) {
			actionMode.finish();
		}
		actionMode = null;
	}

	private void manuallySetTimestamp() {
		final int position = adapter.getSelectedItemIndices().get(0);

		View view = this.getLayoutInflater().inflate(R.layout.dialog_manual_add, null);
		final EditText min = view.findViewById(R.id.manual_minutes_edittext);
		final EditText sec = view.findViewById(R.id.manual_seconds_edittext);
		final EditText mil = view.findViewById(R.id.manual_milliseconds_edittext);

		if (adapter.lyricData.get(position).getTimestamp() != null) {
			min.setText(String.format(Locale.getDefault(), "%02d", adapter.lyricData.get(position).getTimestamp().getMinutes()));
			sec.setText(String.format(Locale.getDefault(), "%02d", adapter.lyricData.get(position).getTimestamp().getSeconds()));
			mil.setText(String.format(Locale.getDefault(), "%02d", adapter.lyricData.get(position).getTimestamp().getMilliseconds() / 10));
		}

		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(view)
				.setPositiveButton(getString(R.string.set), null)
				.setNegativeButton(getString(R.string.cancel), null)
				.setCancelable(false)
				.create();

		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

		dialog.setOnShowListener(d -> {
			Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			b.setOnClickListener(v -> {
				String minText = min.getText().toString().trim();
				String secText = sec.getText().toString().trim();
				String milText = mil.getText().toString().trim();

				if (!minText.matches("^[0-9]+$")) {
					min.setError(getString(R.string.invalid_time));
					return;
				} else if (!secText.matches("^[0-9]+$")) {
					sec.setError(getString(R.string.invalid_time));
					return;
				} else if (!milText.matches("^[0-9]+$")) {
					mil.setError(getString(R.string.invalid_time));
					return;
				}

				if (Integer.parseInt(secText) >= 60) {
					sec.setError(getString(R.string.seconds_less_than_60_message));
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

				Timestamp timestamp = adapter.lyricData.get(position).getTimestamp();
				if (timestamp == null) {
					timestamp = new Timestamp(Long.parseLong(minText),
							Long.parseLong(secText),
							Long.parseLong(milText));
					adapter.lyricData.get(position).setTimestamp(timestamp);
				} else {
					timestamp.setTime(Long.parseLong(minText),
							Long.parseLong(secText),
							Long.parseLong(milText));
				}

				adapter.notifyItemChanged(position);

				dialog.dismiss();
			});
		});

		dialog.show();
		min.requestFocus();
	}

	private void delete() {
		String[] options = {getString(R.string.delete_timestamps_only_message), getString(R.string.delete_timestamps_and_lyrics_message)};
		new AlertDialog.Builder(this)
				.setTitle(R.string.delete_prompt)
				.setSingleChoiceItems(options, 0, null)
				.setPositiveButton(getString(R.string.delete), (dialog, which) -> {
					int selectedOption = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

					List<Integer> selectedItemPositions =
							adapter.getSelectedItemIndices();
					longPressedPos = -1;
					longPressed = 0;
					if (selectedOption == 0) { /* Delete timestamps only */
						for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
							adapter.lyricData.get(selectedItemPositions.get(i)).setTimestamp(null);
							adapter.notifyItemChanged(selectedItemPositions.get(i));
						}
					} else if (selectedOption == 1) { /* Delete both */
						for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
							adapter.lyricData.remove((int) selectedItemPositions.get(i));
							adapter.notifyItemRemoved(selectedItemPositions.get(i));
						}

						if (adapter.lyricData.size() == 0) {
							Toolbar toolbar = findViewById(R.id.toolbar);
							toolbar.getMenu().findItem(R.id.action_add).setVisible(true);
						}
					}

					changedData = true;

					if (actionMode != null) {
						actionMode.finish();
					}
					actionMode = null;
				})
				.setNegativeButton(getString(R.string.cancel), null)
				.create()
				.show();
	}

	private void offsetTimestamps(long milli) {
		List<Integer> selectedItemPositions = adapter.getSelectedItemIndices();

		for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
			Timestamp timestamp = adapter.lyricData.get(selectedItemPositions.get(i)).getTimestamp();
			if (timestamp == null) {
				timestamp = new Timestamp("00:00.00");
				adapter.lyricData.get(selectedItemPositions.get(i)).setTimestamp(timestamp);
			}

			timestamp.alterTimestamp(milli);
			adapter.notifyItemChanged(selectedItemPositions.get(i));
		}
	}

	private void editLyricData(final int optionMode) {
		final int position;
		try {
			position = adapter.getSelectedItemIndices().get(0);
		} catch (IndexOutOfBoundsException e) { // Can occur when the user quickly double taps the menu item
			e.printStackTrace();
			return;
		}

		LayoutInflater inflater = this.getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_edit, null);
		final EditText editText = view.findViewById(R.id.dialog_edittext);
		TextView textView = view.findViewById(R.id.dialog_prompt);

		String hint, positive_button_text;

		if (optionMode != 0) {
			/* Insert lyrics */

			textView.setText(getString(R.string.insert_lyrics_prompt));
			positive_button_text = getString(R.string.insert);

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

		} else {
			/* Edit selected lyric */

			textView.setText(getString(R.string.modified_lyric_prompt));
			editText.setText(adapter.lyricData.get(position).getLyric());

			positive_button_text = getString(R.string.modify);
			hint = getString(R.string.modified_lyric_hint);
			editText.setHint(hint);
		}

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(view)
				.setPositiveButton(positive_button_text, (dialog1, which) -> {

					if (optionMode != 0) {
						/* Insert lyrics */

						String data = editText.getText().toString().trim();

						String[] lyrics = data.split("\\n");
						insertLyrics(lyrics, optionMode);

					} else {
						/* Edit selected lyric */

						changedData = true;

						adapter.lyricData.get(position).setLyric(editText.getText().toString());
						adapter.notifyItemChanged(position);

					}
				})
				.setNegativeButton(getString(R.string.cancel), null)
				.create();


		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

		dialog.show();
	}

	private void insertLyrics(final String[] lyrics, int optionMode) {
		int selectedItemPosition = adapter.getSelectedItemIndices().get(0);
		longPressedPos = -1;
		longPressed = 0;

		if (optionMode == -1) { /* Before */
			for (int i = lyrics.length - 1; i >= 0; i--) {
				adapter.lyricData.add(selectedItemPosition,
						new LyricItem(lyrics[i].trim(), null));
				adapter.notifyItemInserted(selectedItemPosition);
			}
		} else if (optionMode == +1) { /* After */
			for (int i = lyrics.length - 1; i >= 0; i--) {
				adapter.lyricData.add(selectedItemPosition + 1,
						new LyricItem(lyrics[i].trim(), null));
				adapter.notifyItemInserted(selectedItemPosition + 1);
			}
		}

		changedData = true;

		if (optionMode == -1) {
			recyclerView.smoothScrollToPosition(selectedItemPosition);
		} else if (optionMode == +1) {
			recyclerView.smoothScrollToPosition(selectedItemPosition + 1);
		}

		if (actionMode != null) {
			actionMode.finish();
		}
		actionMode = null;
	}

	private void batchEditLyrics() {
		// TODO: Refactor the mess in here

		LayoutInflater inflater = this.getLayoutInflater();
		final View view = inflater.inflate(R.layout.dialog_adjust, null);
		final TextView batchTitle = view.findViewById(R.id.title);
		batchTitle.setText(R.string.batch_edit_prompt);
		final TextView batchTimestamp = view.findViewById(R.id.content);
		batchTimestamp.setText("+00:00.00");

		final Timestamp timestamp = new Timestamp("00:00.00");

		final Handler batchTimestampUpdater = new Handler();
		final int[] longPressed = {0};
		final boolean[] batchTimeNegative = {false}; // Have to use arrays because Java

		final Runnable updateBatchTimestamp = new Runnable() {
			@Override
			public void run() {
				/* `longPressed[0]` will be +1 if the '+' button is pressed */
				/* `longPressed[0]` will be -1 if the '-' button is pressed */
				/* `longPressed[0]` will be 0 if none of the buttons are pressed */
				/* `batchTimeNegative[0]` will be true when the offset is negative; false otherwise */

				if (longPressed[0] != 0) {
					if (longPressed[0] == 1) {
						if (batchTimeNegative[0]) {
							timestamp.alterTimestamp(-timestampAdjustStep);
						} else {
							timestamp.alterTimestamp(timestampAdjustStep);
						}

						if (batchTimeNegative[0] && timestamp.toMilliseconds() <= 0) {
							batchTimeNegative[0] = false;
						}
					} else {
						if (!batchTimeNegative[0] && timestamp.toMilliseconds() - timestampAdjustStep < 0) {
							batchTimeNegative[0] = true;
						}

						if (batchTimeNegative[0]) {
							timestamp.alterTimestamp(timestampAdjustStep);
						} else {
							timestamp.alterTimestamp(-timestampAdjustStep);
						}
					}

					if (!batchTimeNegative[0]) {
						batchTimestamp.setText(
								String.format(Locale.getDefault(), "+%s", timestamp.toString()));
					} else {
						batchTimestamp.setText(
								String.format(Locale.getDefault(), "-%s", timestamp.toString()));
					}

					batchTimestampUpdater.postDelayed(this, 50);
				}
			}
		};


		ImageButton increase = view.findViewById(R.id.increase_button);
		if (isDarkTheme) {
			increase.setImageDrawable(getDrawable(R.drawable.ic_add_light));
		}

		increase.setOnClickListener(v -> {
			if (batchTimeNegative[0]) {
				timestamp.alterTimestamp(-timestampAdjustStep);
			} else {
				timestamp.alterTimestamp(timestampAdjustStep);
			}

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
		});

		increase.setOnLongClickListener(v -> {
			if (longPressed[0] != 1) {
				longPressed[0] = 1;

				batchTimestampUpdater.post(updateBatchTimestamp);
			}
			return false;
		});

		ImageButton decrease = view.findViewById(R.id.decrease_button);
		if (isDarkTheme) {
			decrease.setImageDrawable(getDrawable(R.drawable.ic_minus_light));
		}

		decrease.setOnClickListener(v -> {
			if (!batchTimeNegative[0] && timestamp.toMilliseconds() - timestampAdjustStep < 0) {
				batchTimeNegative[0] = true;
			}

			if (batchTimeNegative[0]) {
				timestamp.alterTimestamp(timestampAdjustStep);
			} else {
				timestamp.alterTimestamp(-timestampAdjustStep);
			}

			if (!batchTimeNegative[0]) {
				batchTimestamp.setText(
						String.format(Locale.getDefault(), "+%s", timestamp.toString()));
			} else {
				batchTimestamp.setText(
						String.format(Locale.getDefault(), "-%s", timestamp.toString()));
			}

			longPressed[0] = 0;
		});

		decrease.setOnLongClickListener(v -> {
			if (longPressed[0] != -1) {
				longPressed[0] = -1;

				batchTimestampUpdater.post(updateBatchTimestamp);
			}
			return false;
		});

		new AlertDialog.Builder(this)
				.setView(view)
				.setTitle(R.string.batch_edit)
				.setPositiveButton(getString(R.string.adjust), (dialog1, which) -> {
					changedData = true;
					longPressed[0] = 0;

					if (batchTimeNegative[0]) {
						offsetTimestamps(-timestamp.toMilliseconds());
					} else {
						offsetTimestamps(timestamp.toMilliseconds());
					}

					if (actionMode != null) {
						actionMode.finish();
					}
					actionMode = null;
				})
				.setNegativeButton(getString(R.string.cancel), (dialog12, which) -> longPressed[0] = 0)
				.setCancelable(false)
				.create()
				.show();
	}

	private void displayPlaybackOptions() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // Will never happen. Added so that Android Studio will stop complaining
			Toast.makeText(this, getString(R.string.playback_options_unsupported_message), Toast.LENGTH_SHORT).show();
			return;
		}

		LayoutInflater inflater = this.getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_playback_options, null);

		final SeekBar speedSeekbar = view.findViewById(R.id.speed_seekbar);
		final SeekBar pitchSeekbar = view.findViewById(R.id.pitch_seekbar);
		final TextView speedDisplayer = view.findViewById(R.id.speed_textview);
		final TextView pitchDisplayer = view.findViewById(R.id.pitch_textview);

		speedSeekbar.setMax(200);
		speedSeekbar.setProgress((int) (currentPlayerSpeed * 100));
		pitchSeekbar.setMax(200);
		pitchSeekbar.setProgress((int) (currentPlayerPitch * 100));

		final int[] speedValue = {speedSeekbar.getProgress()};
		final int[] pitchValue = {pitchSeekbar.getProgress()};

		speedDisplayer.setText(getString(R.string.playback_speed_displayer, speedValue[0]));
		pitchDisplayer.setText(getString(R.string.playback_pitch_displayer, pitchValue[0]));

		speedSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean user) {
				progress = Math.max(progress, 10);
				progress = Math.min(progress, speedSeekbar.getMax());
				speedValue[0] = ((progress / 10) * 10);
				speedDisplayer.setText(getString(R.string.playback_speed_displayer, speedValue[0]));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setSpeed(speedValue[0]);
			}
		});

		pitchSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean user) {
				progress = Math.max(progress, 10);
				progress = Math.min(progress, pitchSeekbar.getMax());
				pitchValue[0] = ((progress / 10) * 10);
				pitchDisplayer.setText(getString(R.string.playback_pitch_displayer, pitchValue[0]));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setPitch(pitchValue[0]);
			}
		});

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(view)
				.setTitle(R.string.player_playback_options_title)
				.setPositiveButton(getString(R.string.ok), null)
				.setNegativeButton(getString(R.string.reset), null)
				.create();

		dialog.setOnShowListener(d -> {
			Button b = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
			b.setOnClickListener(v -> {
				speedSeekbar.setProgress(100);
				pitchSeekbar.setProgress(100);
				setSpeed(100);
				setPitch(100);
			});
		});

		dialog.show();
	}

	private void setSpeed(int speed) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Always true, I know, but it won't compile without it
			try {
				player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed / 100.0f));
				if (!isPlaying) {
					player.pause();
				}
				currentPlayerSpeed = speed / 100.0f;
			} catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
				Toast.makeText(getApplicationContext(), getString(R.string.failed_to_set_speed_message), Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void setPitch(int pitch) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Always true, I know, but it won't compile without it
			try {
				player.setPlaybackParams(player.getPlaybackParams().setPitch(pitch / 100.0f));
				if (!isPlaying) {
					player.pause();
				}
				currentPlayerPitch = pitch / 100.0f;
			} catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
				Toast.makeText(getApplicationContext(), getString(R.string.failed_to_set_pitch_message), Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_editor_activity, menu);
		playbackOptions = menu.findItem(R.id.action_playback_options);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int itemID = item.getItemId();
		if (itemID == R.id.action_done) {
			if (longPressedPos != -1) {
				longPressed = 0;
				longPressedPos = -1;
			}

			if (adapter != null) {
				Intent intent = new Intent(this, FinalizeActivity.class);
				intent.putExtra(IntentSharedStrings.LYRIC_DATA, (ArrayList<LyricItem>) adapter.lyricData);
				intent.putExtra(IntentSharedStrings.SONG_URI, songUri);
				intent.putExtra(IntentSharedStrings.METADATA, metadata);
				intent.putExtra(IntentSharedStrings.SONG_FILE_NAME, songFileName);
				intent.putExtra(IntentSharedStrings.LRC_FILE_NAME, lrcFileName);
				intent.putExtra(IntentSharedStrings.LRC_FILE_PATH, lrcFilePath);
				startActivity(intent);
			}

			return true;
		} else if (itemID == R.id.action_add) {
			adapter.lyricData.add(new LyricItem("", null));
			adapter.notifyItemInserted(0);

			Toolbar toolbar = findViewById(R.id.toolbar);
			toolbar.getMenu().findItem(R.id.action_add).setVisible(false);

			return true;
		} else if (itemID == R.id.action_playback_options) {
			displayPlaybackOptions();
			return true;
		} else if (itemID == R.id.action_collapse_or_expand) {
			collapseOrExpandMediaplayer();
			if (mediaplayerIsCollapsed) {
				item.setIcon(getDrawable(R.drawable.ic_expand_toolbar));
			} else {
				item.setIcon(getDrawable(R.drawable.ic_collapse_toolbar));
			}

			return true;
		} else if (itemID == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);

	}

	@Override
	public void onBackPressed() {
		if (changedData) {
			new AlertDialog.Builder(this)
					.setTitle(getString(R.string.warning))
					.setMessage(R.string.go_back_warning_message)
					.setPositiveButton(getString(R.string.go_back), (dialog, which) -> {
						reset();
						EditorActivity.super.onBackPressed();
					})
					.setNegativeButton(getString(R.string.stay_here), null)
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
			playPause.setImageDrawable(getDrawable(R.drawable.ic_play_light));
		} else {
			playPause.setImageDrawable(getDrawable(R.drawable.ic_play));
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
			adapter.stopFlash(this.pos);

			Handler waiter = new Handler();
			waiter.postDelayed(() -> {
				if (adapter.getFlashingItems().size() == 0) {
					((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
				}
			}, 250);

		}
	}

	private class ActionModeCallback implements ActionMode.Callback {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mode.getMenuInflater().inflate(R.menu.contextual_menu_editor_activity, menu);
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
			int itemID = item.getItemId();
			if (itemID == R.id.action_delete) {
				delete();
				return true;
			} else if (itemID == R.id.action_edit) {
				optionMode = 0;
				editLyricData(optionMode);
				mode.finish();
				return true;
			} else if (itemID == R.id.action_select_all) {
				selectAll();
				return true;
			} else if (itemID == R.id.action_manually_set) {
				manuallySetTimestamp();
				return true;
			} else if (itemID == R.id.action_copy) {
				copy();
				return true;
			} else if (itemID == R.id.action_paste_before) {
				optionMode = -1;
				paste(optionMode);
				return true;
			} else if (itemID == R.id.action_paste_after) {
				optionMode = +1;
				paste(optionMode);
				return true;
			} else if (itemID == R.id.action_insert_before) {
				optionMode = -1;
				editLyricData(optionMode);
				return true;
			} else if (itemID == R.id.action_insert_after) {
				optionMode = +1;
				editLyricData(optionMode);
				return true;
			} else if (itemID == R.id.action_batch_edit) {
				batchEditLyrics();
				return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			adapter.clearSelections();
			actionMode = null;
		}
	}
}
