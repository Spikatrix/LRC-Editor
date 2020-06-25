package com.cg.lrceditor;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class FinalizeActivity extends AppCompatActivity {

	private ArrayList<LyricItem> lyricData;

	private EditText songName;
	private EditText artistName;
	private EditText albumName;
	private EditText composerName;

	private TextView statusTextView;

	private Uri saveUri;
	private String saveLocation;

	private String lrcFileName = null;
	private String songFileName = null;

	private View dialogView;

	private SharedPreferences preferences;

	private boolean isDarkTheme = false;
	private boolean overwriteFailed = false;
	private boolean threadIsExecuting = false;
	private boolean useThreeDigitMilliseconds = false;

	public static void hideKeyboard(Activity activity) {
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
		//Find the currently focused view, so we can grab the correct window token from it.
		View view = activity.getCurrentFocus();
		//If no view currently has focus, create a new one, just so we can grab a window token from it
		if (view == null) {
			view = new View(activity);
		}
		if (imm != null) {
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		saveLocation = preferences.getString("saveLocation", Constants.defaultLocation);
		String uriString = preferences.getString("saveUri", null);
		if (uriString != null)
			saveUri = Uri.parse(uriString);

		if (dialogView != null) {
			((TextView) dialogView.findViewById(R.id.save_location_display)).setText(getString(R.string.save_location_displayer, saveLocation));
		}

		useThreeDigitMilliseconds = preferences.getBoolean("three_digit_milliseconds", false);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString("current_theme", "light");
		if (theme.equals("dark")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDark);
		} else if (theme.equals("darker")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDarker);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_finalize);

		Intent intent = getIntent();
		lyricData = (ArrayList<LyricItem>) intent.getSerializableExtra("LYRIC DATA");
		SongMetaData songMetaData = (SongMetaData) intent.getSerializableExtra("SONG METADATA");
		Uri songUri = intent.getParcelableExtra("SONG URI");
		lrcFileName = intent.getStringExtra("LRC FILE NAME");
		songFileName = intent.getStringExtra("SONG FILE NAME");

		songName = findViewById(R.id.songName_edittext);
		artistName = findViewById(R.id.artistName_edittext);
		albumName = findViewById(R.id.albumName_edittext);
		composerName = findViewById(R.id.composer_edittext);

		statusTextView = findViewById(R.id.status_textview);
		statusTextView.setMovementMethod(new ScrollingMovementMethod());

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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) /* Marshmallow onwards require runtime permissions */
			grantPermission();

		if (songMetaData != null) {
			if (!songMetaData.getSongName().isEmpty())
				songName.setText(songMetaData.getSongName());
			if (!songMetaData.getArtistName().isEmpty())
				artistName.setText(songMetaData.getArtistName());
			if (!songMetaData.getAlbumName().isEmpty())
				albumName.setText(songMetaData.getAlbumName());
			if (!songMetaData.getComposerName().isEmpty())
				composerName.setText(songMetaData.getComposerName());
		}

		if (songUri != null) {
			try {
				MediaMetadataRetriever mmr = new MediaMetadataRetriever();
				mmr.setDataSource(this, songUri);

				if (songName.getText().toString().isEmpty())
					songName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
				if (albumName.getText().toString().isEmpty())
					albumName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
				if (artistName.getText().toString().isEmpty())
					artistName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
				if (composerName.getText().toString().isEmpty())
					composerName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
			} catch (RuntimeException e) {
				Toast.makeText(this, getString(R.string.failed_to_extract_metadata_message), Toast.LENGTH_LONG).show();
			}
		}
	}

	public void displaySaveDialog(View view) {
		if (!isExternalStorageWritable()) {
			Toast.makeText(this, getString(R.string.storage_unavailable_message), Toast.LENGTH_LONG).show();
			return;
		} else if (threadIsExecuting) {
			Toast.makeText(this, getString(R.string.another_operation_wait_message), Toast.LENGTH_SHORT).show();
			return;
		}

		statusTextView.setText(getString(R.string.processing));
		statusTextView.setVisibility(View.VISIBLE);

		Button copyError = findViewById(R.id.copy_error_button);
		copyError.setVisibility(View.GONE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !grantPermission()) { /* Marshmallow onwards require runtime permissions */
			statusTextView.setTextColor(ContextCompat.getColor(this, R.color.errorColor));
			statusTextView.setText(getString(R.string.no_permission_to_write_text));

			copyError = findViewById(R.id.copy_error_button);
			copyError.setVisibility(View.VISIBLE);

			return;
		} else if (!isDarkTheme) {
			statusTextView.setTextColor(Color.BLACK);
		} else {
			statusTextView.setTextColor(Color.WHITE);
		}

		overwriteFailed = false;

		LayoutInflater inflater = this.getLayoutInflater();
		dialogView = inflater.inflate(R.layout.dialog_save_lrc, null);
		final EditText editText = dialogView.findViewById(R.id.dialog_edittext);
		final TextView saveLocationDisplayer = dialogView.findViewById(R.id.save_location_display);
		TextView textView = dialogView.findViewById(R.id.dialog_prompt);
		editText.setHint(getString(R.string.file_name_hint));
		editText.setText(songName.getText().toString() + ".lrc");
		textView.setText(getString(R.string.file_name_prompt));

		saveLocationDisplayer.setText(getString(R.string.save_location_displayer, saveLocation));

		if (songFileName == null) {
			dialogView.findViewById(R.id.same_name_as_song).setEnabled(false);
		}
		if (lrcFileName == null) {
			dialogView.findViewById(R.id.same_name_as_lrc).setEnabled(false);
		}

		AlertDialog dialog = new AlertDialog.Builder(this)
			.setView(dialogView)
			.setPositiveButton(getString(R.string.save), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();

					saveLyricsFile(editText.getText().toString());
				}
			})
			.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					statusTextView.setVisibility(View.GONE);
				}
			})
			.setCancelable(false)
			.create();
		dialog.show();
	}

	private void saveLyricsFile(String name) {
		String path;
		if (saveUri != null) {
			path = FileUtil.getFullPathFromTreeUri(saveUri, this);
		} else {
			path = saveLocation;
		}

		String fileName = name;
		if (fileName.endsWith(".lrc"))
			fileName = fileName.substring(0, fileName.lastIndexOf('.'));

		if (path != null) { // I'm not sure why I wrote this, will this ever be false?
			final File f = new File(path + "/" + fileName + ".lrc");
			if (f.exists()) {
				final String finalFileName = fileName;
				new AlertDialog.Builder(this)
					.setTitle(getString(R.string.warning))
					.setMessage(getString(R.string.overwrite_prompt, fileName, saveLocation))
					.setCancelable(false)
					.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							new Thread(new Runnable() {
								@Override
								public void run() {
									threadIsExecuting = true;

									setStatusOnUiThread(getString(R.string.attempting_to_overwrite_message));
									if (!deletefile(finalFileName)) {
										overwriteFailed = true;
										runOnUiThread(new Runnable() {
											@Override
											public void run() {
												Toast.makeText(getApplicationContext(), getString(R.string.failed_to_overwrite_message), Toast.LENGTH_LONG).show();
											}
										});
									}

									setStatusOnUiThread(getString(R.string.writing_lyrics_message));
									writeLyrics(finalFileName);

									threadIsExecuting = false;
								}
							}).start();
						}
					})
					.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							statusTextView.setVisibility(View.GONE);
						}
					})
					.show();
			} else {
				final String finalFileName = fileName;
				new Thread(new Runnable() {
					@Override
					public void run() {
						threadIsExecuting = true;

						setStatusOnUiThread(getString(R.string.writing_lyrics_message));
						writeLyrics(finalFileName);

						threadIsExecuting = false;
					}
				}).start();
			}
		} else {
			final String finalFileName = fileName;
			new Thread(new Runnable() {
				@Override
				public void run() {
					threadIsExecuting = true;

					setStatusOnUiThread(getString(R.string.writing_lyrics_message));
					writeLyrics(finalFileName);

					threadIsExecuting = false;
				}
			}).start();
		}

		hideKeyboard(this);
	}

	private void setStatusOnUiThread(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				statusTextView.setText(msg);
			}
		});
	}

	private boolean deletefile(String fileName) {
		DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(saveUri, saveLocation, getApplicationContext());

		DocumentFile file = pickedDir.findFile(fileName + ".lrc");
		return file != null && file.delete();
	}

	private void writeLyrics(final String fileName) {
		DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(saveUri, saveLocation, getApplicationContext());
		DocumentFile file = pickedDir.createFile("application/*", fileName + ".lrc");

		try {
			OutputStream out = getContentResolver().openOutputStream(file.getUri());
			InputStream in = new ByteArrayInputStream(lyricsToString(useThreeDigitMilliseconds).getBytes(StandardCharsets.UTF_8));

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();

			out.flush();
			out.close();

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					saveSuccessful(fileName);
				}
			});

		} catch (IOException | NullPointerException | IllegalArgumentException e) {
			e.printStackTrace();
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					statusTextView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.errorColor));
					statusTextView.setText(String.format(Locale.getDefault(), getString(R.string.whoops_error) + "\n%s", e.getMessage()));

					Button copy_error = findViewById(R.id.copy_error_button);
					copy_error.setVisibility(View.VISIBLE);
				}
			});
		}
	}

	private String lyricsToString(boolean useThreeDigitMilliseconds) {
		StringBuilder sb = new StringBuilder();

		String str;
		str = artistName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[ar: ").append(str).append("]\n");
		str = albumName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[al: ").append(str).append("]\n");
		str = songName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[ti: ").append(str).append("]\n");
		str = composerName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[au: ").append(str).append("]\n");

		sb.append("\n")
			.append("[re: ").append(getString(R.string.app_name)).append(" - Android app").append("]\n")
			.append("[ve: ").append("Version ").append(BuildConfig.VERSION_NAME).append("]\n")
			.append("\n");

		for (int i = 0, len = lyricData.size(); i < len; i++) {
			Timestamp timestamp = lyricData.get(i).getTimestamp();
			if (timestamp != null) {
				String lyric = lyricData.get(i).getLyric();
				if (lyric == null || lyric.equals("")) { // Some players might skip empty lyric lines
					lyric = " ";
				}
				if (useThreeDigitMilliseconds) {
					sb.append("[").append(timestamp.toStringWithThreeDigitMilliseconds()).append("]").append(lyric).append("\n");
				} else {
					sb.append("[").append(timestamp.toString()).append("]").append(lyric).append("\n");
				}
			}
		}

		return sb.toString();
	}

	public void setAsLRCFileName(View view) {
		((EditText) dialogView.findViewById(R.id.dialog_edittext)).setText(lrcFileName);
	}

	public void setAsSongFileName(View view) {
		EditText editText = dialogView.findViewById(R.id.dialog_edittext);
		try {
			if (songFileName.contains(".") &&
				(songFileName.lastIndexOf('.') == songFileName.length() - 4 ||
					songFileName.lastIndexOf('.') == songFileName.length() - 5)) {
				editText.setText(songFileName.substring(0, songFileName.lastIndexOf('.')) + ".lrc");
			} else {
				editText.setText(songFileName + ".lrc");
			}
		} catch (IndexOutOfBoundsException e) {
			editText.setText(songFileName + ".lrc");
		}
	}

	private boolean grantPermission() {
		if (ContextCompat.checkSelfPermission(this,
			Manifest.permission.WRITE_EXTERNAL_STORAGE)
			!= PackageManager.PERMISSION_GRANTED) {
			displayDialog();
			return false;
		}
		return true;
	}

	private void saveSuccessful(String fileName) {
		statusTextView.setTextColor(ContextCompat.getColor(this, R.color.successColor));
		if (overwriteFailed)
			statusTextView.setText(getString(R.string.save_successful, saveLocation + "/" + fileName + "<suffix> .lrc"));
		else
			statusTextView.setText(getString(R.string.save_successful, saveLocation + "/" + fileName + ".lrc"));
	}

	private void displayDialog() {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setMessage(getString(R.string.storage_permission_prompt));
		dialog.setTitle(getString(R.string.need_permissions));
		dialog.setCancelable(false);
		dialog.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ActivityCompat.requestPermissions(FinalizeActivity.this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.WRITE_EXTERNAL_REQUEST);
			}
		});
		dialog.show();
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == Constants.WRITE_EXTERNAL_REQUEST) {
			for (int i = 0; i < permissions.length; i++) {
				String permission = permissions[i];
				int grantResult = grantResults[i];

				if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					if (grantResult == PackageManager.PERMISSION_GRANTED) {
						Button button = findViewById(R.id.save_button);
						button.performClick();
						return;
					} else {
						Toast.makeText(this, getString(R.string.cannot_save_without_permission_message), Toast.LENGTH_LONG).show();
					}
				}
			}
		}
	}

	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	public void copyLrc(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText("Generated LRC data", lyricsToString(useThreeDigitMilliseconds));
		if (clipboard != null) {
			clipboard.setPrimaryClip(clip);
		} else {
			Toast.makeText(this, getString(R.string.failed_to_fetch_clipboard_message), Toast.LENGTH_LONG).show();
			return;
		}

		Toast.makeText(this, getString(R.string.copy_lrc_file_data_successful_message), Toast.LENGTH_LONG).show();
	}

	public void copyError(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText("Save Error Info", statusTextView.getText().toString());
		if (clipboard != null) {
			clipboard.setPrimaryClip(clip);
		} else {
			Toast.makeText(this, getString(R.string.failed_to_fetch_clipboard_message), Toast.LENGTH_LONG).show();
			return;
		}

		Toast.makeText(this, getString(R.string.copy_error_successful_message), Toast.LENGTH_LONG).show();
	}

	public void editSaveLocation(View view) {
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}

		return (super.onOptionsItemSelected(item));
	}
}
