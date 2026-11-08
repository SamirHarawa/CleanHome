package com.samir.cleanhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The system asks the "notification handler panel" to open when the remote's settings button is pressed.
 * Google TV's home used to answer; CleanHome answers now and shows its glass quick settings.
 */
public class PanelActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        startActivity(new Intent(this, HomeActivity.class)
                .putExtra("quickSettings", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }
}
