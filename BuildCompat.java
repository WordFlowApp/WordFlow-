package com.wordflow.app;

import android.content.Context;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;

final class BuildCompat {
    static boolean hasNotificationPermission(Context c) {
        return Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
