package com.genymobile.scrcpy.wrappers;

import android.os.IInterface;

public final class PackageManager {
    private final IInterface manager;

    public PackageManager(IInterface manager) {
        this.manager = manager;
    }
}
