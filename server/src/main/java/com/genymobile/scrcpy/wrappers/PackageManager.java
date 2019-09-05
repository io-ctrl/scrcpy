package com.genymobile.scrcpy.wrappers;

import android.os.IInterface;

import com.genymobile.scrcpy.Ln;

public final class PackageManager {
    private final IInterface manager;

    public PackageManager(IInterface manager) {
        this.manager = manager;
    }

    public boolean isPackageAvailable(String packageName) {
        try {
            Class<?> cls = manager.getClass();
            try {
                return (Boolean) cls.getMethod("isPackageAvailable", String.class, int.class).invoke(manager, packageName, 0);
            } catch (NoSuchMethodException e) {
                Ln.e("isPackageAvailable", e);
            }
        } catch (Exception e) {
            Ln.e("isPackageAvailable", e);
        }
        return false;
    }
}
