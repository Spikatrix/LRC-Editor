package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private TextView saveLocation;
    private TextView readLocation;

    private RadioGroup themeGroup;
    private RadioButton light, dark, darker;

    private SharedPreferences preferences;

    private boolean isDarkTheme = false;

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
        setContentView(R.layout.activity_settings);

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

        saveLocation = findViewById(R.id.save_location);
        readLocation = findViewById(R.id.read_location);

        Switch threeDigitMillisecondsSwitch = findViewById(R.id.three_digit_milliseconds_switch);
        threeDigitMillisecondsSwitch.setChecked(preferences.getBoolean("three_digit_milliseconds", false));
        threeDigitMillisecondsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("three_digit_milliseconds", checked);
                editor.apply();
            }
        });

        themeGroup = findViewById(R.id.theme_group);
        light = findViewById(R.id.radioButtonLight);
        dark = findViewById(R.id.radioButtonDark);
        darker = findViewById(R.id.radioButtonDarker);

        if (theme.equals("light")) {
            light.setChecked(true);
        } else if (theme.equals("dark")) {
            dark.setChecked(true);
        } else if (theme.equals("darker")) {
            darker.setChecked(true);
        } else {
            Toast.makeText(this, getString(R.string.unexpected_error_message), Toast.LENGTH_SHORT).show();
        }

        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                SharedPreferences.Editor editor = preferences.edit();

                if (checkedId == light.getId()) {
                    editor.putString("current_theme", "light");
                } else if (checkedId == dark.getId()) {
                    editor.putString("current_theme", "dark");
                } else if (checkedId == darker.getId()) {
                    editor.putString("current_theme", "darker");
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.unexpected_error_message), Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(getApplicationContext(), getString(R.string.restart_for_theme_message), Toast.LENGTH_SHORT).show();
                editor.apply();
                recreate();
            }
        });

        String location = preferences.getString("saveLocation", Constants.defaultLocation);
        saveLocation.setText(location);
        location = preferences.getString("readLocation", Constants.defaultLocation);
        readLocation.setText(location);

        if (preferences.getString("lrceditor_purchased", "").equals("Y")) {
            TextView themeTitle = findViewById(R.id.theme_title);
            RadioGroup themeGroup = findViewById(R.id.theme_group);

            themeTitle.setVisibility(View.VISIBLE);
            themeGroup.setVisibility(View.VISIBLE);
        }
    }

    public void changeReadLocation(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            startActivityForResult(intent, Constants.READ_LOCATION_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Whoops! " + getString(R.string.failed_to_open_directory_picker_message), Toast.LENGTH_SHORT).show();
            Toast.makeText(this, getString(R.string.documentsui_enable_message), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void changeSaveLocation(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            startActivityForResult(intent, Constants.SAVE_LOCATION_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Whoops! " + getString(R.string.failed_to_open_directory_picker_message), Toast.LENGTH_SHORT).show();
            Toast.makeText(this, getString(R.string.documentsui_enable_message), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void showThreeDigitMillisecondsHelp(View view) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.three_digit_milliseconds_help)
                .setNeutralButton(getString(R.string.ok), null)
                .create()
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == Constants.SAVE_LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
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
        } else if (requestCode == Constants.READ_LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                if (uri != null) {
                    SharedPreferences.Editor editor = preferences.edit();

                    String realPath = FileUtil.getFullPathFromTreeUri(uri, this);

                    editor.putString("readLocation", realPath);
                    try {
                        editor.putString("readUri", uri.toString());
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        editor.putString("readUri", null);
                    }
                    editor.apply();

                    readLocation.setText(realPath);

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
