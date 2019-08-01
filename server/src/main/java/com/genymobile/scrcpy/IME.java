package com.genymobile.scrcpy;

import android.content.Intent;

import com.genymobile.scrcpy.wrappers.ActivityManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;

public final class IME {
    private final ActivityManager activityManager = (new ServiceManager()).getActivityManager();

    private boolean enabled = DeviceControl.isAdbIMEEnabled();

    IME() {
    }

    public boolean send(final String text) {
        if (!enabled) return false;
        Intent intent = new Intent();
        intent.setAction("ADB_INPUT_TEXT");
        intent.putExtra("msg", text);
        return activityManager.broadcastIntent(intent);
    }

    public void Finish() {
    }
}
