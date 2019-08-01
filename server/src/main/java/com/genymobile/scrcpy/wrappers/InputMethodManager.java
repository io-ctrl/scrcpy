package com.genymobile.scrcpy.wrappers;

import android.os.IBinder;
import android.os.IInterface;

import com.genymobile.scrcpy.Ln;

public final class InputMethodManager {
    private final IInterface manager;

    public InputMethodManager(IInterface manager) { this.manager = manager; }

    public boolean setInputMethodEnabled(String id, boolean enabled) {
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

    // It does not work as expected!
    public boolean switchToLastInputMethod() {
        try {
            Class<?> cls = manager.getClass();
            try {
                IBinder arg = null;
                return (Boolean) cls.getMethod("switchToLastInputMethod", IBinder.class).invoke(manager, arg);
            } catch (NoSuchMethodException e) {
                Ln.e("switchToLastInputMethod", e);
            }
        } catch (Exception e) {
            Ln.e("switchToLastInputMethod", e);
        }
        return false;
    }
}
