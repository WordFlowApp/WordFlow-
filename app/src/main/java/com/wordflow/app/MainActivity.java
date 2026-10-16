package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.rgb(3, 14, 45));
        getWindow().setNavigationBarColor(Color.rgb(3, 14, 45));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(3, 14, 45));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(18));

        ImageView welcome = new ImageView(this);
        welcome.setImageResource(R.drawable.wordflow_welcome);
        welcome.setAdjustViewBounds(true);
        welcome.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(
                welcome,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        Button startButton = new Button(this);
        startButton.setText("COMENZAR A USAR WORDFLOW  →");
        startButton.setTextColor(Color.rgb(5, 20, 55));
        startButton.setTextSize(16);
        startButton.setAllCaps(false);
        startButton.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(Color.rgb(255, 220, 0));
        buttonBackground.setCornerRadius(dp(24));
        startButton.setBackground(buttonBackground);
        startButton.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, -2);
        buttonParams.setMargins(dp(8), dp(14), dp(8), dp(8));
        content.addView(startButton, buttonParams);

        startButton.setOnClickListener(v -> startTranslator());

        scroll.addView(content);
        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        setContentView(root);
    }

    private void startTranslator() {
        if (Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(this, OverlayService.class);
                startForegroundService(intent);
            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "No se pudo iniciar WordFlow. Intentá nuevamente.",
                        Toast.LENGTH_LONG
                ).show();
            }
        } else {
            Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(permissionIntent);

            Toast.makeText(
                    this,
                    "Activá Mostrar sobre otras aplicaciones y volvé a WordFlow.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
            }
