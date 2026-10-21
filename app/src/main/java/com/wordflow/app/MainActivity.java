package com.wordflow.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_MICROPHONE = 1001;
    private boolean waitingForOverlayPermission = false;
    private boolean launchRequested = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Skip the welcome/presentation screen: the app icon opens the
        // floating translator directly after any required permissions.
        startTranslator();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false;
            startTranslator();
        }
    }

    private void startTranslator() {
        if (launchRequested) return;

        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true;
            Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permissionIntent);
            Toast.makeText(this,
                    "Activá Mostrar sobre otras aplicaciones y volvé a WordFlow 2.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE);
            return;
        }
        launchOverlayService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchOverlayService();
            } else {
                Toast.makeText(this,
                        "El dictado quedó desactivado. Podés usar WordFlow escribiendo el texto.",
                        Toast.LENGTH_LONG).show();
                // Microphone permission is optional; typing remains available.
                launchOverlayService();
            }
        }
    }

    private void launchOverlayService() {
        if (launchRequested) return;
        launchRequested = true;
        try {
            Intent intent = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            finish();
        } catch (Exception e) {
            launchRequested = false;
            Toast.makeText(this,
                    "No se pudo iniciar la ventana flotante. Revisá los permisos e intentá nuevamente.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
