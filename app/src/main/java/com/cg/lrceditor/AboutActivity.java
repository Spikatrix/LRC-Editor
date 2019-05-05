package com.cg.lrceditor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class AboutActivity extends AppCompatActivity {

    private boolean isDarkTheme = false;

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
        setContentView(R.layout.activity_about);

        if (isDarkTheme) {
            Button b = findViewById(R.id.rate_and_review_button);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_open_in_new_light), null);

            b = findViewById(R.id.send_feedback_button);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_open_in_new_light), null);
        }

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

        TextView version = findViewById(R.id.app_version);
        version.setText(String.format(Locale.getDefault(), "Version %s (Build %s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
    }

    public void rate_and_review(View view) {
        Uri uri = Uri.parse("market://details?id=" + this.getPackageName());
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=" + this.getPackageName())));
        }
    }

    public void send_feedback(View view) {
        String deviceInfo = "";
        deviceInfo += "\n OS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
        deviceInfo += "\n OS API Level: " + android.os.Build.VERSION.SDK_INT;
        deviceInfo += "\n Device: " + android.os.Build.DEVICE;
        deviceInfo += "\n Model and Product: " + android.os.Build.MODEL + " (" + android.os.Build.PRODUCT + ")";
        deviceInfo += "\n LRC Editor version " + BuildConfig.VERSION_NAME + " (Build: " + BuildConfig.VERSION_CODE + ")";

        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.dev_email), null));
        intent.putExtra(Intent.EXTRA_SUBJECT, "LRC Editor Feedback");
        intent.putExtra(Intent.EXTRA_TEXT, "Enter your feedback/bug report here\n\n" + deviceInfo);
        startActivity(Intent.createChooser(intent, "Send Feedback:"));
    }

    public void support_us(View view) {
        Intent intent = new Intent(this, SupportActivity.class);
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
