package com.cg.lrceditor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class CreateActivity extends AppCompatActivity {

    private boolean isDarkTheme = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        String theme = preferences.getString("current_theme", "light");
        if (theme.equals("dark")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDark);
        } else if (theme.equals("darker")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDarker);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

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

        if (isDarkTheme) {
            /* Dark border when using a dark theme */
            findViewById(R.id.lyrics_textbox).setBackground(getDrawable(R.drawable.rounded_border_light));
        }
    }


    public void startEditor(View view) {
        EditText editText = findViewById(R.id.lyrics_textbox);
        String data = editText.getText().toString().trim();

        if (data.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_lyrics_found_message), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("LYRICS", data.split("\\n"));
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
