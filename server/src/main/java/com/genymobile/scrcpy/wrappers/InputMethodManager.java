package com.genymobile.scrcpy.wrappers;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import com.genymobile.scrcpy.Ln;

public final class InputMethodManager {
    private final IInterface manager;

    public InputMethodManager(IInterface manager) { this.manager = manager; }

    public boolean setInputMethodEnabled(String id, boolean enabled) {
        // The method was removed in Pie (API 28)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return true;
        try {
            Class<?> cls = manager.getClass();
            try {
                return (Boolean) cls.getMethod("setInputMethodEnabled", String.class, boolean.class).invoke(manager, id, enabled);
            } catch (NoSuchMethodException e) {
                Ln.e("setInputMethodEnabled", e);
            }
        } catch (Exception e) {
            Ln.e("setInputMethodEnabled", e);
        }
        return true; // Don't try to disable
    }

    public boolean setInputMethod(String id) {
        try {
            Class<?> cls = manager.getClass();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                // In Android 10, setInputMethod became deprecated in InputMethodManager, and was removed
                // from IInputMethosManager. The deprecated method works essentially this way:
                    Settings.put(Settings.SECURE, Settings.DEFAULT_INPUT_METHOD, id);
                else
                    cls.getMethod("setInputMethod", IBinder.class, String.class).invoke(manager, null, id);
                return true;
            } catch (NoSuchMethodException e) {
                Ln.e("setInputMethod", e);
            }
        } catch (Exception e) {
            Ln.e("setInputMethod", e);
        }
        return false;
    }
}
