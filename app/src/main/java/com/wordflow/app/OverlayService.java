
package com.wordflow.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private View panel;
    private EditText input;
    private TextView result, direction;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady = false;
    private boolean northToArgentine = true;
    private boolean minimized = false;
    private int panelWidth = 320;
    private int screenWidth;
    private int screenHeight;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

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

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        createNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                setSpeechLanguage();
            }
        });

        showPanel();
    }

    private void createNotification() {
        String channel = "wordflow_overlay";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    channel, "WordFlow flotante",
                    NotificationManager.IMPORTANCE_LOW);

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
        return b;
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(this) {
            private float startDistance;
            private int startWidth, startHeight;
            private boolean pinching = false;

            private float distance(MotionEvent e) {
                if (e.getPointerCount() < 2) return 0;
                float dx = e.getX(0) - e.getX(1);
                float dy = e.getY(0) - e.getY(1);
                return (float) Math.sqrt(dx * dx + dy * dy);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getPointerCount() >= 2
                        || pinching) {
                    int action = event.getActionMasked();

                    if (action == MotionEvent.ACTION_POINTER_DOWN) {
                        startDistance = distance(event);
                        startWidth = params.width;
                        startHeight = panel.getHeight();
                        if (startHeight <= 0) startHeight = dp(300);
                        pinching = true;
                        return true;
                    }

                    if (action == MotionEvent.ACTION_MOVE && pinching) {
                        float d = distance(event);
                        if (startDistance > 0 && d > 0) {
                            float scale = d / startDistance;

                            params.width = Math.max(dp(240),
                                    Math.min(screenWidth - dp(16),
                                            (int) (startWidth * scale)));

                            params.height = Math.max(dp(160),
                                    Math.min(screenHeight - dp(80),
                                            (int) (startHeight * scale)));

                            try {
                                wm.updateViewLayout(panel, params);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    }

                    if (action == MotionEvent.ACTION_POINTER_UP
                            || action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        pinching = false;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        try {
                            wm.updateViewLayout(panel, params);
                        } catch (Exception ignored) {
                        }
                        return true;
                    }

                    return true;
                }

                return super.dispatchTouchEvent(event);
            }
        };

        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(rounded(Color.rgb(8, 29, 75),
                Color.rgb(0, 220, 255)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("WordFlow", 20, Color.CYAN);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button minimize = button("—");
        header.addView(minimize, new LinearLayout.LayoutParams(dp(44), dp(42)));

        Button close = button("×");
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(42)));

        box.addView(header);

        direction = text("🇺🇸  →  🇦🇷", 16, Color.rgb(255, 220, 90));
        direction.setGravity(Gravity.CENTER);
        direction.setPadding(0, dp(8), 0, dp(8));
        box.addView(direction);

        direction.setOnClickListener(v -> {
            northToArgentine = !northToArgentine;
            direction.setText(northToArgentine
                    ? "🇺🇸  →  🇦🇷" : "🇦🇷  →  🇺🇸");
            setSpeechLanguage();
        });

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(8), 0, dp(4), 0);
        inputRow.setBackground(rounded(Color.rgb(35, 55, 100),
                Color.TRANSPARENT));

        TextView inputFlag = text("🇺🇸", 24, Color.WHITE);
        inputFlag.setPadding(0, 0, dp(6), 0);
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

        Button mic = button("🎙");
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button swap = button("🇺🇸 ⇄ 🇦🇷");
        actions.addView(swap, new LinearLayout.LayoutParams(dp(112), dp(48)));

        Button translate = button("Traducir");
        actions.addView(translate, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(actions);

        result = text("La traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(8), dp(12), dp(8), dp(8));
        box.addView(result);

        LinearLayout outputActions = new LinearLayout(this);
        outputActions.setGravity(Gravity.CENTER_VERTICAL);

        TextView outputFlag = text("🇦🇷", 24, Color.WHITE);
        outputActions.addView(outputFlag);

        Button speak = button("🔊");
        outputActions.addView(speak, new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button copy = button("Copiar");
        outputActions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(outputActions);

        TextView resize = text("🤏 Pellizcá para cambiar tamaño · Arrastrá arriba para mover",
                11, Color.LTGRAY);
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
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(120);

        panel = box;

        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.max(0, Math.min(
                                screenWidth - panel.getWidth(),
                                startX + (int) (event.getRawX() - downX)));
                        params.y = Math.max(0, Math.min(
                                screenHeight - dp(80),
                                startY + (int) (event.getRawY() - downY)));
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
            minimized = !minimized;
            inputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            actions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            result.setVisibility(minimized ? View.GONE : View.VISIBLE);
            outputActions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            resize.setVisibility(minimized ? View.GONE : View.VISIBLE);
            direction.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimize.setText(minimized ? "+" : "—");
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try {
                wm.updateViewLayout(panel, params);
            } catch (Exception ignored) {
            }
        });

        close.setOnClickListener(v -> stopSelf());
        swap.setOnClickListener(v -> direction.performClick());
        translate.setOnClickListener(v -> translateText());
        mic.setOnClickListener(v -> startVoiceRecognition());
        speak.setOnClickListener(v -> speakTranslation());

        copy.setOnClickListener(v -> {
            String value = result.getText().toString();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("WordFlow", value));
            Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
        });

        try {
            wm.addView(panel, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void startVoiceRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Primero hay que autorizar el micrófono en Android.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,
                    "El reconocimiento de voz no está disponible en este teléfono.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle b) {
                    handler.post(() -> Toast.makeText(
                            OverlayService.this, "Te escucho…",
                            Toast.LENGTH_SHORT).show());
                }

                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    handler.post(() -> Toast.makeText(
                            OverlayService.this,
                            "No pude reconocer la voz. Probá otra vez.",
                            Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResults(android.os.Bundle bundle) {
                    ArrayList<String> matches = bundle.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        input.setText(matches.get(0));
                        input.setSelection(input.length());
                    }
                }

                @Override public void onPartialResults(android.os.Bundle b) {}
                @Override public void onEvent(int event, android.os.Bundle b) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar el micrófono.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void translateText() {
        String word = input.getText().toString().trim();

        if (word.isEmpty()) {
            result.setText("Escribí o dictá una palabra o frase primero.");
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
                                + q + "&langpair=" + pair);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(),
                                StandardCharsets.UTF_8));

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
                if (result != null) result.setText(output);
            });
        });
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) {
            tts.setLanguage(northToArgentine
                    ? Locale.forLanguageTag("es-AR")
                    : Locale.US);
        }
    }

    private void speakTranslation() {
        if (result == null) return;

        String value = result.getText().toString().trim();
        if (value.isEmpty() || value.equals("Traduciendo…")
                || value.startsWith("La traducción")) {
            Toast.makeText(this, "Primero traducí una frase.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (tts == null || !ttsReady) {
            Toast.makeText(this, "El audio todavía se está preparando.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setSpeechLanguage();
        tts.spe
