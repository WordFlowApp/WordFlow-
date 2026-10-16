
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
    private boolean ttsReady;
    private boolean northToArgentine = true;
    private boolean minimized;
    private final int panelWidth = 320;
    private int screenWidth, screenHeight;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
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
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotification();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                setSpeechLanguage();
            }
        });

        showPanel();
    }

    private void createNotification() {
        String channelId = "wordflow_overlay";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "WordFlow flotante",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        builder.setContentTitle("WordFlow está activo")
                .setContentText("Tu ventana flotante está disponible.")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true);

        startForeground(7, builder.build());
    }

    private GradientDrawable rounded(int color, int borderColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));

        if (borderColor != Color.TRANSPARENT) {
            background.setStroke(dp(1), borderColor);
        }

        return background;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        return view;
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(this) {
            private float initialDistance;
            private int initialWidth, initialHeight;
            private boolean pinching;

            private float fingerDistance(MotionEvent event) {
                if (event.getPointerCount() < 2) return 0;

                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);

                return (float) Math.sqrt(dx * dx + dy * dy);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();

                if (action == MotionEvent.ACTION_POINTER_DOWN
                        && event.getPointerCount() >= 2) {
                    initialDistance = fingerDistance(event);
                    initialWidth = params.width;
                    initialHeight = panel.getHeight();
                    if (initialHeight <= 0) initialHeight = dp(300);
                    pinching = true;
                    return true;
                }

                if (pinching && action == MotionEvent.ACTION_MOVE
                        && event.getPointerCount() >= 2) {
                    float distance = fingerDistance(event);

                    if (initialDistance > 0 && distance > 0) {
                        float scale = distance / initialDistance;

                        params.width = Math.max(
                                dp(240),
                                Math.min(screenWidth - dp(16),
                                        (int) (initialWidth * scale)));

                        params.height = Math.max(
                                dp(160),
                                Math.min(screenHeight - dp(80),
                                        (int) (initialHeight * scale)));

                        try {
                            wm.updateViewLayout(panel, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                }

                if (pinching && (action == MotionEvent.ACTION_POINTER_UP
                        || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL)) {
                    pinching = false;
                    params.height =
                            WindowManager.LayoutParams.WRAP_CONTENT;

                    try {
                        wm.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }
                    return true;
                }

                if (pinching) return true;

                return super.dispatchTouchEvent(event);
            }
        };

        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(rounded(
                Color.rgb(8, 29, 75), Color.rgb(0, 220, 255)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("WordFlow", 20, Color.CYAN);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button minimizeButton = button("—");
        header.addView(minimizeButton,
                new LinearLayout.LayoutParams(dp(44), dp(42)));

        Button closeButton = button("×");
        header.addView(closeButton,
                new LinearLayout.LayoutParams(dp(44), dp(42)));

        box.addView(header);

        direction = text("🇺🇸  →  🇦🇷", 16,
                Color.rgb(255, 220, 90));
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
        inputRow.setBackground(rounded(
                Color.rgb(35, 55, 100), Color.TRANSPARENT));

        inputRow.addView(text("🇺🇸", 24, Color.WHITE));

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint("Escribí una palabra o frase");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setBackgroundColor(Color.TRANSPARENT);

        inputRow.addView(input,
                new LinearLayout.LayoutParams(0, dp(56), 1));

        Button micButton = button("🎙");
        inputRow.addView(micButton,
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button swapButton = button("🇺🇸 ⇄ 🇦🇷");
        actions.addView(swapButton,
                new LinearLayout.LayoutParams(dp(112), dp(48)));

        Button translateButton = button("Traducir");
        actions.addView(translateButton,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(actions);

        result = text("La traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(8), dp(12), dp(8), dp(8));
        box.addView(result);

        LinearLayout outputActions = new LinearLayout(this);
        outputActions.setGravity(Gravity.CENTER_VERTICAL);
        outputActions.addView(text("🇦🇷", 24, Color.WHITE));

        Button speakButton = button("🔊");
        outputActions.addView(speakButton,
                new LinearLayout.LayoutParams(dp(64), dp(48)));

        Button copyButton = button("Copiar");
        outputActions.addView(copyButton,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(outputActions);

        TextView resizeHint = text(
                "🤏 Pellizcá para cambiar tamaño · Arrastrá arriba para mover",
                11, Color.LTGRAY);
        resizeHint.setPadding(0, dp(8), 0, 0);
        box.addView(resizeHint);

        int windowType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(panelWidth),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
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
            public boolean onTouch(View view, MotionEvent event) {
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

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return false;
                }
            }
        });

        minimizeButton.setOnClickListener(v -> {
            minimized = !minimized;

            inputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            actions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            result.setVisibility(minimized ? View.GONE : View.VISIBLE);
            outputActions.setVisibility(
                    minimized ? View.GONE : View.VISIBLE);
            resizeHint.setVisibility(minimized ? View.GONE : View.VISIBLE);
            direction.setVisibility(minimized ? View.GONE : View.VISIBLE);

            minimizeButton.setText(minimized ? "+" : "—");
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;

            try {
                wm.updateViewLayout(panel, params);
            } catch (Exception ignored) {
            }
        });

        closeButton.setOnClickListener(v -> stopSelf());
        swapButton.setOnClickListener(v -> direction.performClick());
        translateButton.setOnClickListener(v -> translateText());
        micButton.setOnClickListener(v -> startVoiceRecognition());
        speakButton.setOnClickListener(v -> speakTranslation());

        copyButton.setOnClickListener(v -> {
            String value = result.getText().toString();

            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(
                            Context.CLIPBOARD_SERVICE);

            clipboard.setPrimaryClip(
                    ClipData.newPlainText("WordFlow", value));

            Toast.makeText(this, "Texto copiado",
                    Toast.LENGTH_SHORT).show();
        });

        try {
            wm.addView(panel, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void startVoiceRecognition() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Autorizá el micrófono desde la pantalla de WordFlow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,
                    "El reconocimiento de voz no está disponible.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(
                    new RecognitionListener() {
                @Override
                public void onReadyForSpeech(android.os.Bundle bundle) {
                    handler.post(() -> Toast.makeText(
                            OverlayService.this,
                            "Te escucho…", Toast.LENGTH_SHORT).show());
                }

                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] buffer) {}
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
                        handler.post(() -> {
                            input.setText(matches.get(0));
                            input.setSelection(input.length());
                        });
                    }
                }

                @Override public void onPartialResults(android.os.Bundle b) {}
                @Override public void onEvent(int event, android.os.Bundle b) {}
            });

            Intent speechIntent =
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    northToArgentine ? "en-US" : "es-AR");

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            speechRecognizer.startListening(speechIntent);

        } catch (Exception e) {
            Toast.makeText(this,
                    "No se pudo iniciar el micrófono.",
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
                String encoded = URLEncoder.encode(word, "UTF-8");
                String pair = northToArgentine ? "en|es" : "es|en";

                URL url = new URL(
                        "https://api.mymemory.translated.net/get?q="
                                + encoded + "&langpair=" + pair);

                HttpURLConnection connection =
                        (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                } finally {
                    connection.disconnect();
                }

                JSONObject json = new JSONObject(response.toString());
                translated = json.getJSONObject("responseData")
                        .getString("translatedText");

                if (!northToArgentine) {
                    translated = ArgentineSpanishAdapter
                            .toArgentineSpanish(translated);
            }
