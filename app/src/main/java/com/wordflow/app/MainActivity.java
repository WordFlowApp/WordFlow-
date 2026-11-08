package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int RED = Color.rgb(235, 0, 20);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean fromBubble = getIntent() != null && getIntent().getBooleanExtra("from_subs_bubble", false);
        if (!fromBubble && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permití que SUBS se muestre sobre otras aplicaciones.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            startOverlay();
        }
        showHome();
    }

    private void startOverlay() {
        Intent service = new Intent(this, SubsOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(Color.WHITE);
        TextView logo = new TextView(this);
        logo.setText("SUBS");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(36);
        logo.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        logo.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF4242, 0xFFD90016, 0xFF9E0010});
        bg.setCornerRadius(32);
        logo.setBackground(bg);
        root.addView(logo, new LinearLayout.LayoutParams(220, 110));
        TextView title = new TextView(this);
        title.setText("Tu acceso flotante está listo");
        title.setTextColor(Color.rgb(35,35,35));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = 28;
        root.addView(title, tlp);
        TextView detail = new TextView(this);
        detail.setText("Salí de SUBS: el icono rojo SUBS permanece disponible sobre otras aplicaciones. Tocá el icono para volver a esta pantalla. El autocompletado Android se incorpora en la siguiente etapa.");
        detail.setTextColor(Color.DKGRAY);
        detail.setTextSize(16);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = 16;
        root.addView(detail, dlp);
        setContentView(root);
    }
}
