package com.wordflow.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_MICROPHONE = 1001;
    private boolean waitingForOverlayPermission = false;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(3, 14, 45));
        getWindow().setNavigationBarColor(Color.rgb(3, 14, 45));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(3, 14, 45));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(22));

        ImageView welcome = new ImageView(this);
        welcome.setImageResource(R.drawable.wordflow_welcome);
        welcome.setAdjustViewBounds(true);
        welcome.setScaleType(ImageView.ScaleType.FIT_CENTER);
        welcome.setContentDescription("Presentación de WordFlow: traducción entre norteamericano y argentino.");
        content.addView(welcome, new LinearLayout.LayoutParams(-1, -2));

        // A real, visible call-to-action is required; the approved poster image
        // itself does not contain a reliably tappable Android button.
        Button start = new Button(this);
        start.setText("🟡  TOCAR PARA INICIAR");
        start.setAllCaps(false);
        start.setTextSize(17);
        start.setTypeface(null, Typeface.BOLD);
        start.setTextColor(Color.rgb(3, 22, 55));
        start.setPadding(dp(16), dp(8), dp(16), dp(8));
        start.setBackground(rounded(Color.rgb(255, 204, 0), 28));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(60));
        startLp.topMargin = dp(14);
        content.addView(start, startLp);
        start.setOnClickListener(v -> startTranslator());

        TextViewCompat.addFooter(this, content, dp(12));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
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
                // Microphone permission is optional; do not block the translator.
                launchOverlayService();
            }
        }
    }

    private void launchOverlayService() {
        try {
            Intent intent = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(this, "Iniciando la ventana flotante de WordFlow 2.",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "No se pudo iniciar la ventana flotante. Revisá los permisos e intentá nuevamente.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static final class TextViewCompat {
        static void addFooter(Activity activity, LinearLayout parent, int padding) {
            android.widget.TextView footer = new android.widget.TextView(activity);
            footer.setText("Gratis  ·  Simple  ·  Rápido  ·  Para todos");
            footer.setTextColor(Color.rgb(190, 214, 255));
            footer.setTextSize(12);
            footer.setGravity(Gravity.CENTER);
            footer.setPadding(0, padding, 0, padding);
            parent.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        }
    }
}
