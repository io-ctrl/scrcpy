package com.genymobile.scrcpy;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.UserHandle;
import android.view.Surface;

import com.genymobile.scrcpy.wrappers.ActivityManager;
import com.genymobile.scrcpy.wrappers.InputMethodManager;
import com.genymobile.scrcpy.wrappers.PackageManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.Settings;
import com.genymobile.scrcpy.wrappers.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class DeviceControl {
    private final ServiceManager serviceManager = new ServiceManager();
    private final WindowManager wm              = serviceManager.getWindowManager();
    private final InputMethodManager imm        = serviceManager.getInputMethodManager();

    private final int initialDensity = wm.getInitialDisplayDensity();
    private final int baseDensity    = wm.getBaseDisplayDensity();
    private final boolean densityChanged;

    private final int oldRotation        = wm.getRotation();
    private final boolean rotationFrozen = wm.isRotationFrozen();
    private boolean orientationChanged   = false;

    private final Point initialSize = new Point(0, 0);
    private final Point baseSize    = new Point(0, 0);
    private final boolean sizeChanged;

    private String baseBrightness;

    private static DeviceControl Instance;
    private final boolean tabletMode;

    private boolean rotateByBroadcast = true;
    static final String BROADCAST_ACTION = "name.lurker.RotateScreen";
    private final ActivityManager activityManager = (new ServiceManager()).getActivityManager();

    public static final String AdbIME = "com.android.adbkeyboard/.AdbIME";
    private boolean disableAdbIme = false;
    private boolean enabledAdbIme = false;
    private String lastIMEMethod = getCurrentIMEMethod();

    enum Rotations {
        UNKNOWN,
        PORTRAIT,
        LANDSCAPE
    }
    private Rotations Rotation = Rotations.UNKNOWN;

    DeviceControl(final Options options) {
        Ln.i("DeviceControl started");
        Instance = this;
        tabletMode = options.getTabletMode();

        // Init AbdIME before rotateByBroadcast!
        try {
            disableAdbIme = !imm.setInputMethodEnabled(AdbIME, true);
            enabledAdbIme = imm.setInputMethod(AdbIME);
            Ln.i("AdbIME enabled: "+enabledAdbIme+" lastMethod: "+lastIMEMethod);
        } catch (Exception e) {
            Ln.w("IME: "+e.toString());
        }

        wm.getInitialDisplaySize(initialSize);
        wm.getBaseDisplaySize(baseSize);

        if (tabletMode) {
            Ln.i("Rotation: " + oldRotation + ", Frozen: " + rotationFrozen + ", Tablet: " + options.getTabletMode());

            baseBrightness = Settings.get(Settings.SYSTEM, Settings.SCREEN_BRIGHTNESS);
            if (!"".equals(baseBrightness)) {
                Ln.i("baseBrightness: " + baseBrightness);
                Settings.put(Settings.SYSTEM, Settings.SCREEN_BRIGHTNESS, "8");
            }
        }

        densityChanged    = setDensity(options.getDensity());
        sizeChanged       = setSize(options.getSize());
        rotateByBroadcast = startRotator();
    }

    private boolean sendRotate(int orientation) {
        if (!rotateByBroadcast) return false;
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION);
        intent.putExtra("o", orientation);
        boolean result = activityManager.broadcastIntent(intent);
        Ln.i("Rotation by broadcast: "+orientation+" "+result);
        return result;
    }

    private boolean sameOrientation(Rotations r) {
        if (Instance.Rotation != r) return false;
        int co = wm.getRotation();
        if (Instance.Rotation == Rotations.PORTRAIT  && (co == Surface.ROTATION_0  || co == Surface.ROTATION_180)) return true;
        if (Instance.Rotation == Rotations.LANDSCAPE && (co == Surface.ROTATION_90 || co == Surface.ROTATION_270)) return true;
        return false;
    }

    static public void setPortrait() {
        if (Instance == null || !Instance.tabletMode || Instance.sameOrientation(Rotations.PORTRAIT)) return;

        if (!Instance.sendRotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
            Instance.wm.freezeRotation(Surface.ROTATION_0);

        Instance.Rotation = Rotations.PORTRAIT;
        Instance.orientationChanged = true;
    }

    static public void setLandscape() {
        if (Instance == null || !Instance.tabletMode || Instance.sameOrientation(Rotations.LANDSCAPE)) return;

        if (!Instance.sendRotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE))
            Instance.wm.freezeRotation(Surface.ROTATION_90);

        Instance.Rotation = Rotations.LANDSCAPE;
        Instance.orientationChanged = true;
    }

    public void Finish() {
        if (baseBrightness != null && !baseBrightness.isEmpty())
            Settings.put(Settings.SYSTEM, Settings.SCREEN_BRIGHTNESS, baseBrightness);

        if (orientationChanged) {
            if (!Instance.sendRotate(-1)) {
                wm.freezeRotation(oldRotation);
                if (!rotationFrozen) wm.thawRotation();
            }
        }

        if (densityChanged) {
            if (baseDensity == initialDensity) wm.clearForcedDisplayDensity();
            else wm.setForcedDisplayDensity(baseDensity);
        }

        if (sizeChanged) {
            if (baseSize.x == initialSize.x && baseSize.y == initialSize.y)
                wm.clearForcedDisplaySize();
            else
                wm.setForcedDisplaySize(baseSize.x, baseSize.y);
        }

        if (enabledAdbIme && !lastIMEMethod.isEmpty()) imm.setInputMethod(lastIMEMethod);
        if (disableAdbIme) imm.setInputMethodEnabled(AdbIME, false);

        Instance = null;
        Ln.i("DeviceControl stopped");
    }

    private boolean setDensity(final int density) {
        if (density <= 0) return false;
        Ln.i("initialDensity: " + initialDensity + ", baseDensity: " + baseDensity + ", New density: " + density);
        wm.setForcedDisplayDensity(density);
        return true;
    }

    private boolean setSize(final Point size) {
        if (size.x <= 0 || size.y <= 0) return false;
        Ln.i("initialSize: " + initialSize.x + "x" + initialSize.y + ", baseSize: " + baseSize.x + "x" + baseSize.y + " New size: " + size.x + "x" + size.y);
        wm.setForcedDisplaySize(size.x, size.y);
        return true;
    }

    public static boolean isAdbIMEEnabled() {
        return Instance != null && Instance.enabledAdbIme;
    }

    static private final String SERVICE = "com.android.adbkeyboard/.Rotate";
    private boolean startRotator() {
        if (!enabledAdbIme) return false;
        return activityManager.startService(SERVICE);
    }

    private static String getCurrentIMEMethod() {
        String[] cmd = new String[]{"dumpsys", "input_method"};
        String result = "";
        try {
            final Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"));
            String line;
            final String mCurMethodId = "  mCurMethodId=";
            while ((line=reader.readLine()) != null) {
                if (line.startsWith(mCurMethodId)) {
                    result = line.substring(mCurMethodId.length());
                    break;
                }
            }
            p.waitFor();
            try {reader.close();} catch (Exception e) {}
        }
        catch (Exception e) {
            Ln.e("DeviceControl.getCurrentIMEMethod", e);
        }
        return result;
    }
}
