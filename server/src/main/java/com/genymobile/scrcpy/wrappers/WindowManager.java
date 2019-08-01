package com.genymobile.scrcpy.wrappers;

import android.graphics.Point;
import android.os.IInterface;
import android.view.Display;
import android.view.IRotationWatcher;

import com.genymobile.scrcpy.Ln;

import java.lang.reflect.Method;

public final class WindowManager {
    private final IInterface manager;

    public WindowManager(IInterface manager) {
        this.manager = manager;
    }
    private Method getRotationMethod;

    public int getRotation() {
        try {
            if (getRotationMethod == null) {
                Class<?> cls = manager.getClass();
                try {
                    getRotationMethod = cls.getMethod("getRotation");
                } catch (NoSuchMethodException e) {
                    // method changed since this commit:
                    // https://android.googlesource.com/platform/frameworks/base/+/8ee7285128c3843401d4c4d0412cd66e86ba49e3%5E%21/#F2
                    getRotationMethod = cls.getMethod("getDefaultDisplayRotation");
                }
            }
            return (Integer)getRotationMethod.invoke(manager);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public void registerRotationWatcher(IRotationWatcher rotationWatcher) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("watchRotation", IRotationWatcher.class).invoke(manager, rotationWatcher);
            } catch (NoSuchMethodException e) {
                // display parameter added since this commit:
                // https://android.googlesource.com/platform/frameworks/base/+/35fa3c26adcb5f6577849fd0df5228b1f67cf2c6%5E%21/#F1
                cls.getMethod("watchRotation", IRotationWatcher.class, int.class).invoke(manager, rotationWatcher, 0);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public int getBaseDisplayDensity() {
        try {
            Class<?> cls = manager.getClass();
            try {
                Object ret = cls.getMethod("getBaseDisplayDensity", int.class).invoke(manager, Display.DEFAULT_DISPLAY);
                return (Integer)ret;
            } catch (NoSuchMethodException e) {
                Ln.e("getBaseDisplayDensity", e);
            }
        } catch (Exception e) {
            Ln.e("getBaseDisplayDensity", e);
        }
        return -1;
    }

    public int getInitialDisplayDensity() {
        try {
            Class<?> cls = manager.getClass();
            try {
                Object ret = cls.getMethod("getInitialDisplayDensity", int.class).invoke(manager, Display.DEFAULT_DISPLAY);
                return (Integer)ret;
            } catch (NoSuchMethodException e) {
                Ln.e("getInitialDisplayDensity", e);
            }
        } catch (Exception e) {
            Ln.e("getInitialDisplayDensity", e);
        }
        return -1;
    }

    public void setForcedDisplayDensity(int density) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("setForcedDisplayDensityForUser", int.class, int.class, int.class).invoke(manager, Display.DEFAULT_DISPLAY, density, ServiceManager.USER_CURRENT);
            } catch (NoSuchMethodException e) {
                cls.getMethod("setForcedDisplayDensity", int.class, int.class).invoke(manager, Display.DEFAULT_DISPLAY, density);
            }
        } catch (Exception e) {
            Ln.e("setForcedDisplayDensity", e);
        }
    }

    public void clearForcedDisplayDensity() {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("clearForcedDisplayDensityForUser", int.class, int.class).invoke(manager, Display.DEFAULT_DISPLAY, ServiceManager.USER_CURRENT);
            } catch (NoSuchMethodException e) {
                Ln.e("clearForcedDisplayDensity", e);
            }
        } catch (Exception e) {
            Ln.e("clearForcedDisplayDensity", e);
        }
    }

    public void getInitialDisplaySize(Point size) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("getInitialDisplaySize", int.class, Point.class).invoke(manager, Display.DEFAULT_DISPLAY, size);
            } catch (NoSuchMethodException e) {
                Ln.e("getInitialDisplaySize", e);
            }
        } catch (Exception e) {
            Ln.e("getInitialDisplaySize", e);
        }
    }

    public void getBaseDisplaySize(Point size) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("getBaseDisplaySize", int.class, Point.class).invoke(manager, Display.DEFAULT_DISPLAY, size);
            } catch (NoSuchMethodException e) {
                Ln.e("getBaseDisplaySize", e);
            }
        } catch (Exception e) {
            Ln.e("getBaseDisplaySize", e);
        }
    }

    public void setForcedDisplaySize(int width, int height) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("setForcedDisplaySize", int.class, int.class, int.class).invoke(manager, Display.DEFAULT_DISPLAY, width, height);
            } catch (NoSuchMethodException e) {
                Ln.e("setForcedDisplaySize", e);
            }
        } catch (Exception e) {
            Ln.e("setForcedDisplaySize", e);
        }
    }

    public void clearForcedDisplaySize() {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("clearForcedDisplaySize", int.class).invoke(manager, Display.DEFAULT_DISPLAY);
            } catch (NoSuchMethodException e) {
                Ln.e("clearForcedDisplaySize", e);
            }
        } catch (Exception e) {
            Ln.e("clearForcedDisplaySize", e);
        }
    }

    public void freezeRotation(int rotation) {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("freezeRotation", int.class).invoke(manager, rotation);
            } catch (NoSuchMethodException e) {
                Ln.e("freezeRotation", e);
            }
        } catch (Exception e) {
            Ln.e("freezeRotation", e);
        }
    }

    public void thawRotation() {
        try {
            Class<?> cls = manager.getClass();
            try {
                cls.getMethod("thawRotation").invoke(manager);
            } catch (NoSuchMethodException e) {
                Ln.e("thawRotation", e);
            }
        } catch (Exception e) {
            Ln.e("thawRotation", e);
        }
    }

    public boolean isRotationFrozen() {
        try {
            Class<?> cls = manager.getClass();
            try {
                return (Boolean) cls.getMethod("isRotationFrozen").invoke(manager);
            } catch (NoSuchMethodException e) {
                Ln.e("isRotationFrozen", e);
            }
        } catch (Exception e) {
            Ln.e("isRotationFrozen", e);
        }
        return false;
    }
/* It does not work for secured Keyguard
    public boolean isKeyguardLocked() {
        try {
            Class<?> cls = manager.getClass();
            try {
                return (Boolean) cls.getMethod("isKeyguardLocked").invoke(manager);
            } catch (NoSuchMethodException e) {
                Ln.e("isKeyguardLocked", e);
            }
        } catch (Exception e) {
            Ln.e("isKeyguardLocked", e);
        }
        return false;
    }

    public void dismissKeyguard() {
        try {
            Class<?> cls = manager.getClass();
            try {
                Class<?> IKeyguardDismissCallback = Class.forName("com.android.internal.policy.IKeyguardDismissCallback");
                cls.getMethod("dismissKeyguard", IKeyguardDismissCallback).invoke(manager, (Object)null);
            } catch (NoSuchMethodException e) {
                Ln.e("dismissKeyguard", e);
            }
        } catch (Exception e) {
            Ln.e("dismissKeyguard", e);
        }
    }
*/
}
