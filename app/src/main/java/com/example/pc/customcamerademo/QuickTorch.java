package com.example.pc.customcamerademo;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class QuickTorch extends Activity {

    private static final String TAG = QuickTorch.class.getSimpleName();

    /**
     * Start Torch when triggered
     */
    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        if (MainActivity.getTorch() == null) {
            Log.d(TAG, "torch == null");
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            Log.d(TAG, "torch != null");
        }
        finish();
    }
}
