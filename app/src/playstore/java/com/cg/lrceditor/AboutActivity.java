package com.cg.lrceditor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.Locale;

public class AboutActivity extends AppCompatActivity {

	private boolean isDarkTheme = false;

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

		TextView maintainerInfo = findViewById(R.id.maintainer_info);
		maintainerInfo.setMovementMethod(LinkMovementMethod.getInstance());

		TextView version = findViewById(R.id.app_version);
		if (BuildConfig.DEBUG) {
			version.setText(String.format(Locale.getDefault(), "Version %s (Debug Build %s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
		} else {
			version.setText(String.format(Locale.getDefault(), "Version %s (Build %s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
		}
	}

	public void rateAndReview(View view) {
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
					Uri.parse("https://play.google.com/store/apps/details?id=" + this.getPackageName())));
		}
	}

	public void sendFeedback(View view) {
		String deviceInfo = "";
		deviceInfo += "\n OS Version: " + System.getProperty("os.version") + "(" + Build.VERSION.INCREMENTAL + ")";
		deviceInfo += "\n OS API Level: " + Build.VERSION.SDK_INT;
		deviceInfo += "\n Device: " + Build.DEVICE;
		deviceInfo += "\n Model and Product: " + Build.MODEL + " (" + Build.PRODUCT + ")";
		deviceInfo += "\n LRC Editor version " + BuildConfig.VERSION_NAME + " (Build: " + BuildConfig.VERSION_CODE + ")";

		Intent selectorIntent = new Intent(Intent.ACTION_SENDTO);
		selectorIntent.setData(Uri.parse("mailto:"));

		Intent emailIntent = new Intent(Intent.ACTION_SEND);
		emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.dev_email)});
		emailIntent.putExtra(Intent.EXTRA_SUBJECT, "LRC Editor Feedback");
		emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_body_prompt) + "\n" + deviceInfo);
		emailIntent.setSelector(selectorIntent);

		startActivity(Intent.createChooser(emailIntent, getString(R.string.send_feedback)));
	}

	public void startSupportActivity(View view) {
		Intent intent = new Intent(this, SupportActivity.class);
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