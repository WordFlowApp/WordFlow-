package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Launcher entry point: no welcome/presentation screen.
 * Opens the floating translator as soon as Android's overlay permission is available.
 */
public class MainActivity extends Activity {
    private boolean permissionSettingsOpened = false;
    private boolean serviceLaunchAttempted = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Deliberately do not create a layout or presentation screen.
        openTranslatorOrRequestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When the user returns from Android Settings, continue straight to the overlay.
        if (permissionSettingsOpened && Settings.canDrawOverlays(this)) {
            permissionSettingsOpened = false;
            launchTranslator();
        }
    }

    private void openTranslatorOrRequestPermission() {
        if (Settings.canDrawOverlays(this)) {
            launchTranslator();
            return;
        }

        permissionSettingsOpened = true;
        Toast.makeText(this,
                "Para abrir Word Flow, activá «Mostrar sobre otras aplicaciones».",
                Toast.LENGTH_LONG).show();
        Intent permissionIntent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(permissionIntent);
        } catch (Exception e) {
            permissionSettingsOpened = false;
            Toast.makeText(this,
                    "No se pudieron abrir los permisos. Activá «Mostrar sobre otras aplicaciones» desde Ajustes.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void launchTranslator() {
        if (serviceLaunchAttempted) return;
        serviceLaunchAttempted = true;

        Intent serviceIntent = new Intent(this, OverlayService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            finish();
        } catch (Exception e) {
            serviceLaunchAttempted = false;
            Toast.makeText(this,
                    "Word Flow no pudo abrir la ventana flotante. Verificá los permisos e intentá otra vez.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
