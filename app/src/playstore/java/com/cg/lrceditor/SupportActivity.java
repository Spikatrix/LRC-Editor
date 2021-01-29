package com.cg.lrceditor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SupportActivity extends AppCompatActivity {
	private PurchaseItem[] purchaseItems;
	private BillingClient billingClient;

	private SharedPreferences preferences;
	private boolean isDarkTheme = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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

		initPurchaseItems();
		initBilling();
	}

	private void initPurchaseItems() {
		purchaseItems = new PurchaseItem[]{
				new PurchaseItem("SKU1", findViewById(R.id.one)),
				new PurchaseItem("SKU2", findViewById(R.id.two)),
				new PurchaseItem("SKU3", findViewById(R.id.three)),
				new PurchaseItem("SKU4", findViewById(R.id.four)),
				new PurchaseItem("SKU5", findViewById(R.id.five)),
		};
	}

	/**
	 * Initializes billing modules
	 */
	private void initBilling() {
		billingClient = BillingClient.newBuilder(this)
				.setListener((billingResult, purchases) -> {
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
							&& purchases != null) {
						for (Purchase purchase : purchases) {
							handlePurchase(purchase);
						}
					} else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED
							&& billingResult.getResponseCode() != BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
						alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_complete_purchase_message), billingResult.getResponseCode()));
					}
				})
				.enablePendingPurchases()
				.build();

		initConnection();
	}

	private void handlePurchase(Purchase purchase) {
		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
			if (!purchase.isAcknowledged()) {
				AcknowledgePurchaseParams acknowledgePurchaseParams =
						AcknowledgePurchaseParams.newBuilder()
								.setPurchaseToken(purchase.getPurchaseToken())
								.build();
				billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
						grantPurchasePerks();
						purchaseItems[getSkuIndex(purchase.getSku())].setPurchased();
						queryPurchaseHistoryAsync(); // Update purchases
					} else {
						alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_acknowledge_purchase), billingResult.getResponseCode()));
					}
				});
			}
		} else {
			alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_validate_purchase), purchase.getPurchaseState()));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		queryPurchases();
	}

	/**
	 * Connects billing with Google Play servers
	 */
	private void initConnection() {
		if (billingClient.isReady()) {
			// Already connected, call endConnection() to disconnect
			return;
		}

		// Not really an issue if this gets called multiple times as those cases are handled internally
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					// The BillingClient is ready
					queryInventory();
				}
			}

			@Override
			public void onBillingServiceDisconnected() {
				alert(getString(R.string.error), getString(R.string.failed_to_connect_to_gplay));
			}
		});
	}

	/**
	 * Queries for available products
	 */
	private void queryInventory() {
		if (!billingClient.isReady()) {
			// Billing wasn't initialized
			initConnection();
			return;
		}

		List<String> skuStringList = getSkuStringList();
		SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
		params.setSkusList(skuStringList).setType(BillingClient.SkuType.INAPP);
		billingClient.querySkuDetailsAsync(params.build(),
				(billingResult, skuDetailsList) -> {
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
						if (skuDetailsList != null) {
							// Sort the sku list according to the sku string order
							// Because Google sorts it alphabetically for some reason
							String[] skuStrings = skuStringList.toArray(new String[0]);
							Collections.sort(skuDetailsList, (skuDetails, skuDetails2) -> {
								for (String skuString : skuStrings) {
									if (skuDetails.getSku().equals(skuString)) {
										return -1;
									} else if (skuDetails2.getSku().equals(skuString)) {
										return 1;
									}
								}
								return 0;
							});

							// Update the purchase items with the sku
							for (int i = 0; i < skuDetailsList.size(); i++) {
								purchaseItems[i].setSku(skuDetailsList.get(i));
							}
						}

						queryPurchaseHistoryAsync();
					} else {
						alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_load_products), billingResult.getResponseCode()));
					}
				});
	}

	private List<String> getSkuStringList() {
		List<String> skuStringList = new ArrayList<>();
		for (PurchaseItem purchaseItem : purchaseItems) {
			skuStringList.add(purchaseItem.getSkuString());
		}
		return skuStringList;
	}

	/**
	 * Queries and updates recent purchase history including cancelled ones
	 */
	private void queryPurchaseHistoryAsync() {
		if (!billingClient.isReady()) {
			// Billing wasn't initialized
			initConnection();
			return;
		}

		billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, (billingResult, list) -> {
			if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
				if (list != null) {
					queryPurchases();
				}
			} else {
				alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_check_purchase_history), billingResult.getResponseCode()));
			}
		});
	}

	/**
	 * Queries purchases made by using the local Google Play Store cache data
	 */
	private void queryPurchases() {
		if (!billingClient.isReady()) {
			// Billing wasn't initialized
			initConnection();
			return;
		}

		Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
		List<Purchase> purchaseList = purchasesResult.getPurchasesList();
		if (purchasesResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
			if (purchaseList != null) {
				boolean invalidSku = false;

				for (int i = 0; i < purchaseList.size(); i++) {
					// Treats both PURCHASED and UNSPECIFIED purchases as purchased
					// Not sure why some purchases are UNSPECIFIED, maybe it's because a purchase could not be validated?

					int skuIndex = getSkuIndex(purchaseList.get(i).getSku());
					if (skuIndex == -1) {
						// Invalid SKU found
						invalidSku = true;
						break;
					}

					if (purchaseList.get(i).getPurchaseState() != Purchase.PurchaseState.PENDING) {
						purchaseItems[skuIndex].setPurchased();
						grantPurchasePerks();
					}
				}

				if (invalidSku) {
					// Invalid SKU returned. LRC Editor's code on GitHub does not expose the original SKUs for security purposes
					alert(getString(R.string.error), getString(R.string.could_not_validate_skus));
				} else if (purchaseList.size() == 0) {
					// Nothing has been purchased; revoke perks
					revokePurchasePerks();
				}
			}
		} else {
			alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_check_local_purchase), purchasesResult.getResponseCode()));
		}

		for (PurchaseItem purchaseItem : purchaseItems) {
			purchaseItem.updateButtonText(this);
		}
	}

	public void makePurchase(View view) {
		int index = Integer.parseInt(view.getTag().toString());
		SkuDetails skuDetails = purchaseItems[index - 1].getSku();
		if (skuDetails != null) {
			BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
					.setSkuDetails(purchaseItems[index - 1].getSku())
					.build();
			int responseCode = billingClient.launchBillingFlow(this, billingFlowParams).getResponseCode();
			if (responseCode != BillingClient.BillingResponseCode.OK) {
				alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_open_purchase_dialog), responseCode));
			}
		} else {
			alert(getString(R.string.please_wait), getString(R.string.iaps_loading_message));
		}
	}

	private void grantPurchasePerks() {
		if (!preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
			alert(getString(R.string.purchase_successful), getString(R.string.thank_you_for_the_purchase_message));

			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(Constants.PURCHASED_PREFERENCE, "Y");
			editor.apply();
		}
	}

	private void revokePurchasePerks() {
		if (preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(Constants.PURCHASED_PREFERENCE, "");
			editor.putString(Constants.THEME_PREFERENCE, "light");
			editor.apply();
		}
	}

	private int getSkuIndex(String skuString) {
		for (int i = 0; i < purchaseItems.length; i++) {
			PurchaseItem purchaseItem = purchaseItems[i];
			if (purchaseItem.getSkuString().equals(skuString)) {
				return i;
			}
		}

		return -1;
	}

	private void alert(String title, String message) {
		try {
			new AlertDialog.Builder(SupportActivity.this)
					.setTitle(title)
					.setMessage(message)
					.setPositiveButton(getString(R.string.ok), null)
					.create()
					.show();
		} catch (WindowManager.BadTokenException e) {
			// Looks like the app is in the background, can't use an AlertDialog
			Log.w("LRC Editor - Support", "[" + title + "] " + message);
		}
	}

	private String getErrorCodeString(String error, int errorCode) {
		return error + ".\n" + getString(R.string.error_code, errorCode);
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