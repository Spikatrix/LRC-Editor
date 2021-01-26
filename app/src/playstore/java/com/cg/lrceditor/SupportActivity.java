package com.cg.lrceditor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.cg.lrceditor.IAP.IabHelper;
import com.cg.lrceditor.IAP.IabResult;
import com.cg.lrceditor.IAP.Inventory;
import com.cg.lrceditor.IAP.Purchase;
import com.cg.lrceditor.IAP.SkuDetails;

import java.util.ArrayList;

public class SupportActivity extends AppCompatActivity {

	private final SKUItem[] skuItems = {
			new SKUItem("SKU1",   "S_PURCHASE"),
			new SKUItem("SKU2",   "M_PURCHASE"),
			new SKUItem("SKU3",   "L_PURCHASE"),
			new SKUItem("SKU4",  "XL_PURCHASE"),
			new SKUItem("SKU5", "XXL_PURCHASE"),
	};

	IabHelper mHelper;

	private SharedPreferences preferences;
	private Button[] purchaseButtons = new Button[skuItems.length];
	private Context ctx;
	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		@Override
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			if (mHelper == null)
				return;

			if (result.isFailure()) {
				complain(getString(R.string.error) + " (Inventory): " + result);
				setPurchaseButtonTexts(ctx.getString(R.string.error));
				return;
			}

			for (int i = 0; i < skuItems.length; i++) {
				SkuDetails item = inventory.getSkuDetails(skuItems[i].getSku());
				if (item != null) {
					purchaseButtons[i].setText(item.getPrice());
				} else {
					purchaseButtons[i].setText(R.string.error);
				}

				Purchase purchase = inventory.getPurchase(skuItems[i].getSku());
				if (purchase != null && purchase.getPurchaseState() == 0) { // 0 means purchased
					if (!preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
						Toast.makeText(ctx, getString(R.string.dark_themes_available_message), Toast.LENGTH_LONG).show();
					}

					SharedPreferences.Editor editor = preferences.edit();
					editor.putString(Constants.PURCHASED_PREFERENCE, "Y");
					editor.apply();

					purchaseButtons[i].setEnabled(false);
					purchaseButtons[i].setText(R.string.purchased);

				}
			}
		}
	};
	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		@Override
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {

			if (mHelper == null) {
				return;
			}

			if (result.isFailure()) {
				complain(getString(R.string.failed_to_complete_purchase_message) + ": " + result);
			} else {
				for (SKUItem skuItem : skuItems) {
					if (purchase.getSku().equals(skuItem.getSku())) {
						new AlertDialog.Builder(ctx)
								.setTitle(getString(R.string.purchase_successful))
								.setMessage(getString(R.string.thank_you_for_the_purchase_message))
								.setNeutralButton(getString(R.string.ok), null)
								.create()
								.show();
						try {
							mHelper.queryInventoryAsync(true, getSkuList(), null, mGotInventoryListener);
						} catch (IabHelper.IabAsyncInProgressException e) {
							complain(getString(R.string.error) + " (Finish:IabAsyncInProgress)");
							setPurchaseButtonTexts(ctx.getString(R.string.error));
							e.printStackTrace();
						}
						break;
					}
				}
			}
		}
	};

	private boolean isDarkTheme = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ctx = this;

		preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString(Constants.THEME_PREFERENCE, "light");
		if (theme.equals("dark")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDark);
		} else if (theme.equals("darker")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDarker);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_support);

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

		purchaseButtons[0] = findViewById(R.id.one);
		purchaseButtons[1] = findViewById(R.id.two);
		purchaseButtons[2] = findViewById(R.id.three);
		purchaseButtons[3] = findViewById(R.id.four);
		purchaseButtons[4] = findViewById(R.id.five);

		String base64EncodedPublicKey = "Base64EncodedString";
		mHelper = new IabHelper(ctx, base64EncodedPublicKey);

		if (base64EncodedPublicKey.equals("Base64" + "EncodedString")) {
			complain("IAPs won't work as the original encoded key is not used (for security purposes). " +
					"Download and install the APK release from the Play Store if you want to use IAPs");
			setPurchaseButtonTexts(getString(R.string.error));
		} else {
			mHelper.startSetup(result -> {
				if (!result.isSuccess()) {
					complain(getString(R.string.iap_setup_failed_message) + ": " + result);
					setPurchaseButtonTexts(ctx.getString(R.string.error));
					return;
				}

				if (mHelper == null)
					return;

				try {
					mHelper.queryInventoryAsync(true, getSkuList(), null, mGotInventoryListener);
				} catch (IabHelper.IabAsyncInProgressException e) {
					complain(getString(R.string.error) + " (Setup:IabAsyncInProgress)");
					setPurchaseButtonTexts(ctx.getString(R.string.error));
					e.printStackTrace();
				}
			});
		}
	}

	public ArrayList<String> getSkuList() {
		ArrayList<String> list = new ArrayList<>();
		for (SKUItem skuItem : skuItems) {
			list.add(skuItem.getSku());
		}
		return list;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}

		return (super.onOptionsItemSelected(item));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mHelper != null) {
			mHelper.disposeWhenFinished();
			mHelper = null;
		}
	}

	private void setPurchaseButtonTexts(String text) {
		for (Button purchaseButton : purchaseButtons) {
			purchaseButton.setText(text);

			if (text.equals(ctx.getString(R.string.error))) {
				purchaseButton.setEnabled(false);
			}
		}
	}

	public void makePurchase(View view) {
		int purchaseIndex = Integer.parseInt(view.getTag().toString());

		try {
			mHelper.launchPurchaseFlow(this, skuItems[purchaseIndex - 1].getSku(), purchaseIndex,
					mPurchaseFinishedListener, skuItems[purchaseIndex - 1].getExtraString());
		} catch (IabHelper.IabAsyncInProgressException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			complain("IAPs still loading");
		}
	}

	void complain(String complaint) {
		alert("Billing Error: " + complaint);
	}

	void alert(String message) {
		new AlertDialog.Builder(ctx)
				.setMessage(message)
				.setNeutralButton(getString(R.string.ok), null)
				.create()
				.show();
	}

	private class SKUItem {
		private final String sku;
		private final String extraString;

		SKUItem(String sku, String extraString) {
			this.sku = sku;
			this.extraString = extraString;
		}

		public String getSku() {
			return sku;
		}

		public String getExtraString() {
			return extraString;
		}
	}
}
