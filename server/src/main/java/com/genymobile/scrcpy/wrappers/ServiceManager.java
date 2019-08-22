package com.genymobile.scrcpy.wrappers;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

import com.genymobile.scrcpy.Ln;

@SuppressLint("PrivateApi")
public final class ServiceManager {
    private final Method getServiceMethod;
    private final Method checkServiceMethod;

    private WindowManager      windowManager;
    private DisplayManager     displayManager;
    private InputManager       inputManager;
    private PowerManager       powerManager;
    private StatusBarManager   statusBarManager;
    private ClipboardManager   clipboardManager;
    private ActivityManager    activityManager;
    private InputMethodManager inputMethodManager;
    private PackageManager     packageManager;

    public static final int USER_CURRENT = -2;

    public ServiceManager() {
        try {
            getServiceMethod   = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService",   String.class);
            checkServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("checkService", String.class);
        } catch (Exception e) {
            Ln.e("ServiceManager", e);
            throw new AssertionError(e);
        }
    }

    private IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) getServiceMethod.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            Ln.e("getService("+service+")", e);
            throw new AssertionError(e);
        }
    }

    private IInterface checkService(String service, String type) {
        try {
            IBinder binder = (IBinder) checkServiceMethod.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type).getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            Ln.e("checkService("+service+")", e);
            throw new AssertionError(e);
        }
    }

    public WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = new WindowManager(getService("window", "android.view.IWindowManager"));
        }
        return windowManager;
    }

    public DisplayManager getDisplayManager() {
        if (displayManager == null) {
            displayManager = new DisplayManager(getService("display", "android.hardware.display.IDisplayManager"));
        }
        return displayManager;
    }

    public InputManager getInputManager() {
        if (inputManager == null) {
            inputManager = new InputManager(getService("input", "android.hardware.input.IInputManager"));
        }
        return inputManager;
    }

    public PowerManager getPowerManager() {
        if (powerManager == null) {
            powerManager = new PowerManager(getService("power", "android.os.IPowerManager"));
        }
        return powerManager;
    }

    public StatusBarManager getStatusBarManager() {
        if (statusBarManager == null) {
            statusBarManager = new StatusBarManager(getService("statusbar", "com.android.internal.statusbar.IStatusBarService"));
        }
        return statusBarManager;
    }

    public ClipboardManager getClipboardManager() {
        if (clipboardManager == null) {
            clipboardManager = new ClipboardManager(getService("clipboard", "android.content.IClipboard"));
        }
        return clipboardManager;
    }

    public ActivityManager getActivityManager() {
        if (activityManager == null) {
            // This does not work for Android 6
            // activityManager = new ActivityManager(getService("activity", "android.app.IActivityManager"));
            // This works for both Android 6 and 8
            activityManager = new ActivityManager(checkService("activity", "android.app.ActivityManagerNative"));
        }
        return activityManager;
    }

    public InputMethodManager getInputMethodManager() {
        if (inputMethodManager == null) {
            inputMethodManager = new InputMethodManager(getService("input_method", "com.android.internal.view.IInputMethodManager"));
        }
        return inputMethodManager;
    }

    public PackageManager getPackageManager() {
        if (packageManager == null) {
            packageManager = new PackageManager(getService("package", "android.content.pm.IPackageManager"));
        }
        return packageManager;
    }
}
