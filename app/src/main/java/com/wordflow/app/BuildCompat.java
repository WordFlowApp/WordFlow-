
package com.wordflow.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class BuildCompat {

    private BuildCompat() {
    }

    static boolean hasNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT < 33
            || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
          }
