
package com.wordflow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {

    private WindowManager wm;
    private View panel;
    private EditText input;
    private TextView result;
    private TextView direction;
    private WindowManager.LayoutParams params;
    private boolean northToArgentine = true;
    private int panelWidth = 320;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        createNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showPanel();
    }

    private void createNotification() {
        String channel = "wordflow_overlay";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                channel,
                "WordFlow flotante",
                NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            manager.createNotificationChannel(c);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, channel)
            : new Notification.Builder(this);

        builder.setContentTitle("WordFlow está activo")
            .setContentText("Tu ventana flotante está disponible.")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true);

        startForeground(7, builder.build());
    }

    private GradientDrawable rounded(int color, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));

        if (strokeColor != Color.TRANSPARENT) {
            bg.setStroke(dp(1), strokeColor);
        }

        return bg;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(rounded(Color.rgb(8, 29, 75), Color.rgb(0, 220, 255)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("WordFlow  🇺🇸 ⇄ 🇦🇷", 18, Color.CYAN);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button minimize = new Button(this);
        minimize.setText("—");
        header.addView(minimize, new LinearLayout.LayoutParams(dp(48), dp(42)));

        Button close = new Button(this);
        close.setText("×");
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(42)));

        box.addView(header);

        direction = text("🇺🇸  →  🇦🇷", 15, Color.rgb(255, 220, 90));
        direction.setPadding(0, dp(8), 0, dp(8));
        box.addView(direction);

        direction.setOnClickListener(v -> {
            northToArgentine = !northToArgentine;
            direction.setText(northToArgentine ? "🇺🇸  →  🇦🇷" : "🇦🇷  →  🇺🇸");
            input.setHint("Escribí una palabra o frase");
        });

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(8), 0, dp(4), 0);
        inputRow.setBackground(rounded(Color.rgb(35, 55, 100), Color.TRANSPARENT));

        TextView inputFlag = text("🇺🇸", 24, Color.WHITE);
        inputFlag.setPadding(0, 0, dp(8), 0);
        inputRow.addView(inputFlag);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint("Escribí una palabra o frase");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setBackgroundColor(Color.TRANSPARENT);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(56), 1));

        Button mic = new Button(this);
        mic.setText("🎙");
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));

        box.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button swap = new Button(this);
        swap.setText("⇄");
        actions.addView(swap, new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button translate = new Button(this);
        translate.setText("Traducir");
        actions.addView(translate, new LinearLayout.LayoutParams(0, dp(48), 1));

        box.addView(actions);

        result = text("La traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(8), dp(12), dp(8), dp(8));
        box.addView(result);

        LinearLayout outputActions = new LinearLayout(this);
        outputActions.setGravity(Gravity.CENTER_VERTICAL);

        TextView outputFlag = text("🇦🇷", 24, Color.WHITE);
        outputActions.addView(outputFlag);

        Button speak = new Button(this);
        speak.setText("🔊");
        outputActions.addView(speak, new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button copy = new Button(this);
        copy.setText("Copiar");
        outputActions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));

        box.addView(outputActions);

        TextView resize = text("↘ Arrastrá para mover · ↕ Cambiar tamaño", 12, Color.LTGRAY);
        resize.setPadding(0, dp(8), 0, 0);
        box.addView(resize);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            dp(panelWidth),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(120);

        panel = box;

        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (event.getRawX() - downX);
                        params.y = Math.max(0,
                            startY + (int) (event.getRawY() - downY));

                        try {
                            wm.updateViewLayout(panel, params);
                        } catch (Exception ignored) {
                        }
                        return true;
                }
                return false;
            }
        });

        minimize.setOnClickListener(v -> {
            boolean hidden = result.getVisibility() == View.GONE;

            result.setVisibility(hidden ? View.VISIBLE : View.GONE);
            inputRow.setVisibility(hidden ? View.VISIBLE : View.GONE);
            actions.setVisibility(hidden ? View.VISIBLE : View.GONE);
            outputActions.setVisibility(hidden ? View.VISIBLE : View.GONE);
            resize.setVisibility(hidden ? View.VISIBLE : View.GONE);

            minimize.setText(hidden ? "—" : "+");
        });

        close.setOnClickListener(v -> stopSelf());
        swap.setOnClickListener(v -> direction.performClick());
        translate.setOnClickListener(v -> translateText());

        mic.setOnClickListener(v ->
            Toast.makeText(
                this,
                "El dictado por voz se incorporará mediante el reconocimiento de voz de Android.",
                Toast.LENGTH_LONG
            ).show()
        );

        speak.setOnClickListener(v ->
            Toast.makeText(
                this,
                "La reproducción de audio se incorporará en la próxima etapa.",
                Toast.LENGTH_LONG
            ).show()
        );

        copy.setOnClickListener(v -> {
            String value = result.getText().toString();

            ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            clipboard.setPrimaryClip(ClipData.newPlainText("WordFlow", value));

            Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
        });

        resize.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private int startWidth;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    startWidth = params.width;
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int newWidth = startWidth + (int) (event.getRawX() - downX);
                    params.width = Math.max(dp(240), Math.min(dp(420), newWidth));

                    try {
                        wm.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }

                    return true;
                }

                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });

        try {
            wm.addView(panel, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void translateText() {
        String word = input.getText().toString().trim();

        if (word.isEmpty()) {
            result.setText("Escribí una palabra o frase primero.");
            return;
        }

        result.setText("Traduciendo…");

        worker.execute(() -> {
            String translated;

            try {
                String q = URLEncoder.encode(word, "UTF-8");
                String pair = northToArgentine ? "en|es" : "es|en";

                URL url = new URL(
                    "https://api.mymemory.translated.net/get?q="
                    + q + "&langpair=" + pair
                );

                HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        conn.getInputStream(),
                        StandardCharsets.UTF_8
                    )
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(response.toString());
                translated = json.getJSONObject("responseData")
                    .getString("translatedText");

            } catch (Exception e) {
                translated = "No se pudo traducir. Revisá internet e intentá otra vez.";
            }

            final String output = translated;

            handler.post(() -> {
                if (result != null) {
                    result.setText(output);
                }
            });
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (panel != null && wm != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
        }

        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
