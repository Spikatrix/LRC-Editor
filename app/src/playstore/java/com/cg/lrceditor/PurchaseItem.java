package com.cg.lrceditor;

import android.content.Context;
import android.widget.Button;

import com.android.billingclient.api.SkuDetails;

public class PurchaseItem {
	private final String skuString;
	private final Button purchaseButton;
	private SkuDetails sku;
	private boolean purchased;

	public PurchaseItem(String skuString, Button purchaseButton) {
		this.skuString = skuString;
		this.purchaseButton = purchaseButton;
		this.sku = null;
		this.purchased = false;
		this.purchaseButton.setEnabled(false);
	}

	public void updateButtonText(Context ctx) {
		if (sku != null) {
			if (purchased) {
				purchaseButton.setText(ctx.getString(R.string.purchased));
				purchaseButton.setEnabled(false);
			} else {
				purchaseButton.setText(sku.getPrice());
				purchaseButton.setEnabled(true);
			}
		} else {
			purchaseButton.setText(ctx.getString(R.string.error));
			purchaseButton.setEnabled(false);
		}
	}

	public void setPurchased() {
		purchased = true;
	}

	public String getSkuString() {
		return skuString;
	}

	public SkuDetails getSku() {
		return sku;
	}

	public void setSku(SkuDetails sku) {
		this.sku = sku;
	}

	public boolean isPurchased() {
		return purchased;
	}
}
