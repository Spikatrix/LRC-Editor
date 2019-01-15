package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST = 1;
    private TextView saveLocation;

    private RadioGroup themeGroup;
    private RadioButton light, dark;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getApplicationContext().getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        if (preferences.getString("current_theme", "").equals("dark")) {
            setTheme(R.style.AppThemeDark);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        try {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        saveLocation = findViewById(R.id.save_location);

        themeGroup = findViewById(R.id.theme_group);
        light = findViewById(R.id.radioButtonLight);
        dark = findViewById(R.id.radioButtonDark);

        if (preferences.getString("current_theme", "").equals("dark")) {
            dark.setChecked(true);
        }

        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                SharedPreferences.Editor editor = preferences.edit();

                if (checkedId == dark.getId()) {
                    editor.putString("current_theme", "dark");
                } else if (checkedId == light.getId()) {
                    editor.putString("current_theme", "light");
                } else {
                    Toast.makeText(getApplicationContext(), "An unexpected error occurred", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(getApplicationContext(), "Restart the app if the theme does not apply", Toast.LENGTH_SHORT).show();
                editor.apply();
                recreate();
            }
        });

        String location = preferences.getString("saveLocation", Environment.getExternalStorageDirectory().getPath() + "/Lyrics");
        saveLocation.setText(location);

        if (preferences.getString("lrceditor_purchased", "").equals("Y")) {
            TextView themeTitle = findViewById(R.id.theme_title);
            RadioGroup themeGroup = findViewById(R.id.theme_group);

            themeTitle.setVisibility(View.VISIBLE);
            themeGroup.setVisibility(View.VISIBLE);
        }
    }

    public void changeSaveLocation(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            startActivityForResult(intent, LOCATION_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Whoops! Couldn't open the directory picker!", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "Are you running Android 5.0+? Maybe enable DocumentsUI from your phone settings", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                if (uri != null) {
                    SharedPreferences.Editor editor = preferences.edit();

                    String realPath = FileUtil.getFullPathFromTreeUri(uri, this);

                    editor.putString("saveLocation", realPath);
                    try {
                        editor.putString("saveUri", uri.toString());
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        editor.putString("saveUri", null);
                    }
                    editor.apply();
                    saveLocation.setText(realPath);

                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
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
