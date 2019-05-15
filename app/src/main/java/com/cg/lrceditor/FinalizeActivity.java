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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class FinalizeActivity extends AppCompatActivity {

    private ArrayList<ItemData> lyricData;

    private Uri uri;

    private EditText songName;
    private EditText artistName;
    private EditText albumName;
    private EditText composerName;

    private TextView resultTextView;

    private String saveLocation;
    private Uri saveUri;

    private String lrcFileName = null;
    private String songFileName = null;

    private boolean overwriteFailed = false;

    private View dialogView;

    private SharedPreferences preferences;

    private boolean isDarkTheme = false;

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
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        String theme = preferences.getString("current_theme", "default_light");
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
        lyricData = (ArrayList<ItemData>) intent.getSerializableExtra("lyricData");
        SongMetaData songMetaData = (SongMetaData) intent.getSerializableExtra("SONG METADATA");
        uri = intent.getParcelableExtra("URI");
        lrcFileName = intent.getStringExtra("LRC FILE NAME");
        songFileName = intent.getStringExtra("SONG FILE NAME");

        songName = findViewById(R.id.songName_edittext);
        artistName = findViewById(R.id.artistName_edittext);
        albumName = findViewById(R.id.albumName_edittext);
        composerName = findViewById(R.id.composer_edittext);

        resultTextView = findViewById(R.id.result_textview);
        resultTextView.setMovementMethod(new ScrollingMovementMethod());

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

        if (preferences.getString("current_theme", "").equals("dark")) {
            resultTextView.setTextColor(Color.WHITE);
        }

        if (Build.VERSION.SDK_INT >= 23) /* 23 = Marshmellow */
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

        if (uri != null) {
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(this, uri);

                if (songName.getText().toString().isEmpty())
                    songName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                if (albumName.getText().toString().isEmpty())
                    albumName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                if (artistName.getText().toString().isEmpty())
                    artistName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                if (composerName.getText().toString().isEmpty())
                    composerName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
            } catch (RuntimeException e) {
                Toast.makeText(this, "Failed to extract media metadata", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        saveLocation = preferences.getString("saveLocation", Environment.getExternalStorageDirectory().getPath() + "/Lyrics");
        String uriString = preferences.getString("saveUri", null);
        if (uriString != null)
            saveUri = Uri.parse(uriString);

        if (dialogView != null) {
            ((TextView) dialogView.findViewById(R.id.save_location_display)).setText("Save location: " + saveLocation);
        }
    }

    public void saveLyrics(View view) {
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, "ERROR: Storage unavailable/busy", Toast.LENGTH_LONG).show();
            return;
        }

        resultTextView.setText(getString(R.string.processing_string));
        resultTextView.setVisibility(View.VISIBLE);

        Button copy_error = findViewById(R.id.copy_error_button);
        copy_error.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= 23 && !grantPermission()) /* 23 = Marshmellow */
            return;

        overwriteFailed = false;

        LayoutInflater inflater = this.getLayoutInflater();
        dialogView = inflater.inflate(R.layout.save_lrc_dialog, null);
        final EditText editText = dialogView.findViewById(R.id.dialog_edittext);
        TextView saveLocationDisplayer = dialogView.findViewById(R.id.save_location_display);
        TextView textView = dialogView.findViewById(R.id.dialog_prompt);
        editText.setHint(getString(R.string.file_name_here_prompt));
        editText.setText(songName.getText().toString() + ".lrc");
        textView.setText(getString(R.string.save_file_name_prompt));

        saveLocationDisplayer.setText("Save location: " + saveLocation);

        if (songFileName == null) {
            dialogView.findViewById(R.id.same_name_as_song).setEnabled(false);
        }
        if (lrcFileName == null) {
            dialogView.findViewById(R.id.same_name_as_lrc).setEnabled(false);
        }


        final Context ctx = this;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        final String path;
                        if (saveUri != null) {
                            path = FileUtil.getFullPathFromTreeUri(saveUri, ctx);
                        } else {
                            path = saveLocation;
                        }

                        String fileName = editText.getText().toString();
                        if (fileName.endsWith(".lrc"))
                            fileName = fileName.substring(0, fileName.lastIndexOf('.'));

                        if (path != null) {
                            final File f = new File(path + "/" + fileName + ".lrc");
                            if (f.exists()) {
                                final String finalFileName = fileName;
                                new AlertDialog.Builder(ctx)
                                        .setTitle("Warning")
                                        .setMessage("File '" + fileName + ".lrc' already exists in " + saveLocation + ". " +
                                                "Are you sure you want to overwrite it?")
                                        .setCancelable(false)
                                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (!deletefile(finalFileName)) {
                                                    overwriteFailed = true;
                                                    Toast.makeText(getApplicationContext(), "Failed to overwrite file; Suffix will be appended to the file name", Toast.LENGTH_LONG).show();
                                                }

                                                writeLyricsExternal(finalFileName);
                                            }
                                        })
                                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                resultTextView.setVisibility(View.GONE);
                                            }
                                        })
                                        .show();
                            } else {
                                writeLyricsExternal(fileName);
                            }
                        } else {
                            writeLyricsExternal(fileName);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resultTextView.setVisibility(View.GONE);
                    }
                })
                .setCancelable(false)
                .create();
        dialog.show();

    }

    private void writeLyricsExternal(String fileName) {
        DocumentFile pickedDir;
        try {
            pickedDir = DocumentFile.fromTreeUri(this, saveUri);
            try {
                getContentResolver().takePersistableUriPermission(saveUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            pickedDir = DocumentFile.fromFile(new File(saveLocation));
        }

        DocumentFile file = pickedDir.createFile("application/*", fileName + ".lrc");
        try {
            OutputStream out = getContentResolver().openOutputStream(file.getUri());
            InputStream in = new ByteArrayInputStream(lyricsToString().getBytes(StandardCharsets.UTF_8));

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            out.flush();
            out.close();

            saveSuccessful(fileName);

        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            resultTextView.setTextColor(Color.parseColor("#c61b1b"));
            resultTextView.setText(String.format(Locale.getDefault(), "Whoops! An Error Occurred!\n%s", e.getMessage()));

            Button copy_error = findViewById(R.id.copy_error_button);
            copy_error.setVisibility(View.VISIBLE);
        }

        hideKeyboard(this);
    }

    private boolean deletefile(String fileName) {
        DocumentFile pickedDir;
        try {
            pickedDir = DocumentFile.fromTreeUri(this, saveUri);
            try {
                getContentResolver().takePersistableUriPermission(saveUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            pickedDir = DocumentFile.fromFile(new File(saveLocation));
        }

        DocumentFile file = pickedDir.findFile(fileName + ".lrc");
        return file != null && file.delete();
    }

    private void saveSuccessful(String fileName) {
        resultTextView.setTextColor(Color.rgb(45, 168, 26));
        if (overwriteFailed)
            resultTextView.setText(String.format(Locale.getDefault(), "Successfully wrote the lyrics file at %s",
                    saveLocation + "/" + fileName + "<suffix> .lrc"));
        else
            resultTextView.setText(String.format(Locale.getDefault(), "Successfully wrote the lyrics file at %s",
                    saveLocation + "/" + fileName + ".lrc"));
    }

    private String lyricsToString() {
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
                if (lyric == null || lyric.equals("")) {
                    lyric = " ";
                }
                sb.append("[").append(timestamp.toString()).append("]").append(lyric).append("\n");
            }
        }

        return sb.toString();
    }

    public void setAsSongFileName(View view) {
        EditText editText = dialogView.findViewById(R.id.dialog_edittext);
        try {
            if (songFileName.contains("."))
                editText.setText(songFileName.substring(0, songFileName.lastIndexOf('.')) + ".lrc");
            else
                editText.setText(songFileName + ".lrc");
        } catch (IndexOutOfBoundsException e) {
            editText.setText(songFileName + ".lrc");
        }
    }

    public void setAsLRCFileName(View view) {
        ((EditText) dialogView.findViewById(R.id.dialog_edittext)).setText(lrcFileName);
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

    private void displayDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage("This app needs the storage permission for saving the lyric files");
        dialog.setTitle("Need permissions");
        dialog.setCancelable(false);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
                        Button button = findViewById(R.id.done_button);
                        button.performClick();
                        return;
                    } else {
                        Toast.makeText(this, "Cannot save the file without the storage permission", Toast.LENGTH_LONG).show();
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

    public void copy_lrc(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Generated LRC data", lyricsToString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        } else {
            Toast.makeText(this, "Failed to fetch the System Clipboard Service!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Successfully copied the LRC file data into the System Clipboard!", Toast.LENGTH_LONG).show();
    }

    public void copy_error(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Save Error Info", resultTextView.getText().toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        } else {
            Toast.makeText(this, "Failed to fetch the System Clipboard Service!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Successfully copied the generated error into the System Clipboard!", Toast.LENGTH_LONG).show();
    }

    public void editSaveLocation(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return (super.onOptionsItemSelected(item));
    }
}
