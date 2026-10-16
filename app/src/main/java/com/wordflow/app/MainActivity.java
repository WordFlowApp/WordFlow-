
package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(5, 20, 55));

        TextView title = new TextView(this);
        title.setText("Word");
        title.setTextColor(Color.WHITE);
        title.setTextSize(42);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView flow = new TextView(this);
        flow.setText("Flow");
        flow.setTextColor(Color.rgb(0, 220, 255));
        flow.setTextSize(52);
        flow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        flow.setGravity(Gravity.CENTER);
        root.addView(flow, new LinearLayout.LayoutParams(-1, -2));

        TextView flags = new TextView(this);
        flags.setText("🇺🇸   ⇄   🇦🇷");
        flags.setTextSize(34);
        flags.setGravity(Gravity.CENTER);
        flags.setPadding(0, dp(16), 0, dp(22));
        root.addView(flags, new LinearLayout.LayoutParams(-1, -2));

        TextView intro = new TextView(this);
        intro.setText(
            "Argentina para el mundo y el mundo para Argentina.\n\n" +
            "Aprendé a comprender el idioma natural argentino " +
            "y a comunicarte con el mundo."
        );
        intro.setTextColor(Color.WHITE);
        intro.setTextSize(18);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, 0, 0, dp(24));
        root.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        Button test = new Button(this);
        test.setText("PROBAR TRADUCTOR");
        root.addView(test, new LinearLayout.LayoutParams(-1, -2));

        test.setOnClickListener(v -> {
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
        });

        TextView note = new TextView(this);
        note.setText(
            "\nPrimera versión de prueba\n" +
            "La traducción necesita conexión a internet."
        );
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }
          }
  
