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
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {

    private WindowManager wm;
    private View panel;
    private EditText input;
    private TextView result;
    private TextView direction;
    private TextView inputFlag;
    private TextView outputFlag;
    private WindowManager.LayoutParams params;

    private boolean northToArgentine = true;
    private boolean minimized = false;
    private int panelWidth = 320;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

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

        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
            }
        });

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

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(
                Color.rgb(0, 115, 220),
                Color.rgb(0, 220, 255)
        ));
        return b;
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(rounded(
                Color.rgb(8, 29, 75),
                Color.rgb(0, 220, 255)
        ));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("WordFlow", 20, Color.CYAN);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(8));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button minimize = button("—");
        header.addView(minimize, new LinearLayout.LayoutParams(dp(44), dp(42)));

        Button close = button("×");
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(42)));

        box.addView(header);

        direction = text("🇺🇸 → 🇦🇷", 16, Color.rgb(255, 220, 90));
        direction.setGravity(Gravity.CENTER);
        direction.setPadding(0, dp(10), 0, dp(10));
        box.addView(direction);
        direction.setOnClickListener(v -> toggleDirection());

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(8), 0, dp(4), 0);
        inputRow.setBackground(rounded(
                Color.rgb(35, 55, 100),
                Color.TRANSPARENT
        ));

        inputFlag = text("🇺🇸", 24, Color.WHITE);
        inputFlag.setPadding(0, 0, dp(8), 0);
        inputRow.addView(inputFlag);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint("Escribí una palabra o frase...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(4), dp(8), dp(4), dp(8));
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1));

        Button mic = button("🎙");
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, dp(4));

        Button swap = button("⇄");
        actions.addView(swap, new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button translate = button("Traducir");
        translate.setTextColor(Color.rgb(8, 29, 75));
        translate.setBackground(rounded(
                Color.rgb(0, 220, 255),
                Color.rgb(0, 220, 255)
        ));
        actions.addView(translate, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(actions);

        result = text("La traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(8), dp(14), dp(8), dp(12));
        box.addView(result);

        LinearLayout outputActions = new LinearLayout(this);
        outputActions.setGravity(Gravity.CENTER_VERTICAL);

        outputFlag = text("🇦🇷", 24, Color.WHITE);
        outputActions.addView(outputFlag);

        Button speak = button("🔊");
        outputActions.addView(speak, new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button copy = button("Copiar");
        outputActions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(outputActions);

        TextView resize = text(
                "⋮⋮ Arrastrá el título para mover · ↔ Cambiar ancho",
                11,
                Color.LTGRAY
        );
        resize.setPadding(0, dp(10), 0, 0);
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

        title.setOnTouchListener(new View.OnTouchListener() {
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
                        params.y = Math.max(
                                0,
                                startY + (int) (event.getRawY() - downY)
                        );

                        int screenWidth = getResources()
                                .getDisplayMetrics().widthPixels;
                        params.x = Math.max(
                                0,
                                Math.min(params.x, screenWidth - params.width)
                        );

                        try {
                            wm.updateViewLayout(panel, params);
                        } catch (Exception ignored) {
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });

        minimize.setOnClickListener(v -> {
            minimized = !minimized;
            inputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            actions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            result.setVisibility(minimized ? View.GONE : View.VISIBLE);
            outputActions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            resize.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimize.setText(minimized ? "+" : "—");
        });

        close.setOnClickListener(v -> stopSelf());
        swap.setOnClickListener(v -> toggleDirection());
        translate.setOnClickListener(v -> translateText());

        mic.setOnClickListener(v -> Toast.makeText(
                this,
                "El dictado por voz todavía requiere integrar el reconocimiento de Android.",
                Toast.LENGTH_LONG
        ).show());

        speak.setOnClickListener(v -> speakTranslation());

        copy.setOnClickListener(v -> {
            String value = result.getText().toString();

            if (value.isEmpty()
                    || value.equals("La traducción aparecerá aquí.")
                    || value.equals("Traduciendo…")) {
                Toast.makeText(this, "Todavía no hay una traducción.", Toast.LENGTH_SHORT).show();
                return;
            }

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
                    int screenWidth = getResources()
                            .getDisplayMetrics().widthPixels;

                    params.width = Math.max(
                            dp(240),
                            Math.min(Math.min(dp(420), screenWidth), newWidth)
                    );

                    try {
                        wm.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }
                    return true;
                }

                return event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL;
            }
        });

        try {
            wm.addView(panel, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void toggleDirection() {
        northToArgentine = !northToArgentine;
        direction.setText(northToArgentine ? "🇺🇸 → 🇦🇷" : "🇦🇷 → 🇺🇸");
        inputFlag.setText(northToArgentine ? "🇺🇸" : "🇦🇷");
        outputFlag.setText(northToArgentine ? "🇦🇷" : "🇺🇸");
        input.setHint(northToArgentine
                ? "Escribí en norteamericano..."
                : "Escribí en argentino...");
        result.setText("La traducción aparecerá aquí.");
    }

    private void translateText() {
        final String original = input.getText().toString().trim();

        if (original.isEmpty()) {
            result.setText("Escribí una palabra o frase primero.");
            return;
        }

        final boolean translateToArgentine = northToArgentine;
        final String sourceText = translateToArgentine
                ? original
                : ArgentineSpanishAdapter
                        .prepareArgentineInputForTranslation(original);

        result.setText("Traduciendo…");

        worker.execute(() -> {
            String translated;
            HttpURLConnection conn = null;

            try {
                String q = URLEncoder.encode(
                        sourceText,
                        StandardCharsets.UTF_8.name()
                );

                String pair = translateToArgentine ? "en|es" : "es|en";
                URL url = new URL(
                        "https://api.mymemory.translated.net/get?q="
                                + q + "&langpair=" + pair
                );

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                StandardCharsets.UTF_8
                        ))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());
                    translated = json.getJSONObject("responseData")
                            .getString("translatedText");
                }

                if (translateToArgentine) {
                    translated = ArgentineSpanishAdapter
                            .toArgentineSpanish(translated);
                }

            } catch (Exception e) {
                translated = "No se pudo traducir. Revisá internet e intentá otra vez.";
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            final String output = translated;
            handler.post(() -> {
                if (result != null) {
                    result.setText(output);
                }
            });
        });
    }

    private void speakTranslation() {
        if (result == null) {
            return;
        }

        String value = result.getText().toString().trim();

        if (value.isEmpty()
                || value.equals("La traducción aparecerá aquí.")
                || value.equals("Traduciendo…")
                || value.startsWith("No se pudo traducir.")) {
            Toast.makeText(this, "Primero traducí una frase.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(
                    this,
                    "El audio se está preparando. Probá otra vez.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Locale language = northToArgentine
                ? new Locale("es", "AR")
                : Locale.US;

        int status = textToSpeech.setLanguage(language);

        if (status == TextToSpeech.LANG_MISSING_DATA
                || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(
                    this,
                    "Android no tiene instalada una voz compatible.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        textToSpeech.speak(
                value,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "wordflow_translation"
        );
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

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }
