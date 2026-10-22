package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Launcher activity: no presentation screen; open the floating translator directly. */
public class MainActivity extends Activity {
    private boolean waitingForOverlayPermission = false;
    private boolean launchRequested = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // No layout, welcome screen, or microphone prompt at startup.
        openTranslator();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false;
            openTranslator();
        }
    }

    private void openTranslator() {
        if (launchRequested) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true;
            Toast.makeText(this,
                    "Activá «Mostrar sobre otras aplicaciones» para abrir la ventana flotante.",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                waitingForOverlayPermission = false;
                Toast.makeText(this,
                        "Abrí Ajustes y habilitá «Mostrar sobre otras aplicaciones» para Word Flow.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        launchRequested = true;
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            finish();
        } catch (Exception e) {
            launchRequested = false;
            Toast.makeText(this,
                    "Word Flow no pudo abrir la ventana. Revisá el permiso de superposición e intentá otra vez.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
