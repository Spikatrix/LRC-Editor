package com.cg.lrceditor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.cg.lrceditor.IAP.IabHelper;
import com.cg.lrceditor.IAP.IabResult;
import com.cg.lrceditor.IAP.Inventory;
import com.cg.lrceditor.IAP.Purchase;
import com.cg.lrceditor.IAP.SkuDetails;

import java.util.Arrays;

public class SupportActivity extends AppCompatActivity {

    private static final String[] ITEM_SKUS = {
            "SKU1",
            "SKU2",
            "SKU3",
            "SKU4",
            "SKU5",
    };

    private static final String TAG = "lrceditor.inappbilling";
    IabHelper mHelper;

    private SharedPreferences preferences;

    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Inventory query complete");

            if (mHelper == null)
                return;

            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }

            for (int i = 0; i < ITEM_SKUS.length; i++) {
                SkuDetails item = inventory.getSkuDetails(ITEM_SKUS[i]);
                if (item != null) {
                    purchaseButtons[i].setText(item.getPrice());
                } else {
                    purchaseButtons[i].setText(R.string.error_string);
                }

                Purchase purchase = inventory.getPurchase(ITEM_SKUS[i]);
                if (purchase != null && purchase.getPurchaseState() == 0) { //0 means purchased
                    if (!preferences.getString("lrceditor_purchased", "").equals("Y")) {
                        Toast.makeText(ctx, "Dark themes should now be available in the settings", Toast.LENGTH_LONG).show();
                    }

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("lrceditor_purchased", "Y");
                    editor.apply();

                    purchaseButtons[i].setEnabled(false);
                    purchaseButtons[i].setText(R.string.purchased_string);

                }
            }
        }
    };

    private Button[] purchaseButtons = new Button[5];

    private Context ctx;
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {

            if (mHelper == null) {
                return;
            }

            if (result.isFailure()) {
                complain("Failed to complete the purchase: " + result);
            } else {
                for (String SKU : ITEM_SKUS) {
                    if (purchase.getSku().equals(SKU)) {
                        new AlertDialog.Builder(ctx)
                                .setTitle("Purchase Successful")
                                .setMessage("Thank you so much for the purchase! Dark themes are now available in the settings")
                                .setNeutralButton("OK", null)
                                .create()
                                .show();
                        try {
                            mHelper.queryInventoryAsync(true, Arrays.asList(ITEM_SKUS), null, mGotInventoryListener);
                        } catch (IabHelper.IabAsyncInProgressException e) {
                            complain("Failed to query purchases:Finish:IabAsyncInProgress");
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
        String theme = preferences.getString("current_theme", "default_light");
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

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    complain("In-app Billing setup failed: " + result);
                    return;
                }

                if (mHelper == null)
                    return;

                Log.d(TAG, "In-app Billing is set up OK");

                try {
                    mHelper.queryInventoryAsync(true, Arrays.asList(ITEM_SKUS), null, mGotInventoryListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    complain("Failed to query purchases:Setup:IabAsyncInProgress");
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }

    public void onePurchase(View view) {
        try {
            mHelper.launchPurchaseFlow(this, ITEM_SKUS[0], 1,
                    mPurchaseFinishedListener, "S_PURCHASE");
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            complain("IAPs still loading");
        }
    }

    public void twoPurchase(View view) {
        try {
            mHelper.launchPurchaseFlow(this, ITEM_SKUS[1], 2,
                    mPurchaseFinishedListener, "M_PURCHASE");
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            complain("IAPs still loading");
        }
    }

    public void threePurchase(View view) {
        try {
            mHelper.launchPurchaseFlow(this, ITEM_SKUS[2], 3,
                    mPurchaseFinishedListener, "L_PURCHASE");
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            complain("IAPs still loading");
        }
    }

    public void fourPurchase(View view) {
        try {
            mHelper.launchPurchaseFlow(this, ITEM_SKUS[3], 4,
                    mPurchaseFinishedListener, "XL_PURCHASE");
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            complain("IAPs still loading");
        }
    }

    public void fivePurchase(View view) {
        try {
            mHelper.launchPurchaseFlow(this, ITEM_SKUS[4], 5,
                    mPurchaseFinishedListener, "XXL_PURCHASE");
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            complain("IAPs still loading");
        }
    }

    void complain(String complaint) {
        Log.e(TAG, "Billing error: " + complaint);
        alert("Billing Error: " + complaint);
    }

    void alert(String message) {
        new AlertDialog.Builder(ctx)
                .setMessage(message)
                .setNeutralButton("OK", null)
                .create()
                .show();
    }
}
