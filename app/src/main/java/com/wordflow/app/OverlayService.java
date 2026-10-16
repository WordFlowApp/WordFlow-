
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
    private LinearLayout panel;
    private EditText input;
    private TextView result, direction;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady;
    private boolean northToArgentine = true;
    private boolean minimized;
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

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
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

        Notification notification = builder
                .setContentTitle("WordFlow está activo")
                .setContentText("Tu traductor flotante está disponible")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .build();

        startForeground(7, notification);
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(18));
        return shape;
    }

    private TextView makeText(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private void showPanel() {
        panel = new LinearLayout(this) {
            private float startDistance;
            private int startWidth, startHeight;
            private boolean resizing;

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
                    startDistance = fingerDistance(event);
                    startWidth = params.width;
                    startHeight = panel.getHeight();
                    if (startHeight <= 0) startHeight = dp(320);
                    resizing = true;
                    return true;
                }

                if (resizing && action == MotionEvent.ACTION_MOVE
                        && event.getPointerCount() >= 2) {
                    float distance = fingerDistance(event);

                    if (startDistance > 0 && distance > 0) {
                        float scale = distance / startDistance;

                        params.width = Math.max(dp(230),
                                Math.min(screenWidth - dp(16),
                                        (int) (startWidth * scale)));

                        params.height = Math.max(dp(170),
                                Math.min(screenHeight - dp(80),
                                        (int) (startHeight * scale)));

                        try {
                            wm.updateViewLayout(panel, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                }

                if (resizing && (action == MotionEvent.ACTION_POINTER_UP
                        || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL)) {
                    resizing = false;
                    params.height =
                            WindowManager.LayoutParams.WRAP_CONTENT;
                    try {
                        wm.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }
                    return true;
                }

                if (resizing) return true;
                return super.dispatchTouchEvent(event);
            }
        };

        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(rounded(Color.rgb(8, 29, 75)));
        panel.setElevation(dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = makeText("WordFlow", 20, Color.CYAN);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button minimizeButton = makeButton("—");
        header.addView(minimizeButton,
                new LinearLayout.LayoutParams(dp(44), dp(42)));

        Button closeButton = makeButton("×");
        header.addView(closeButton,
                new LinearLayout.LayoutParams(dp(44), dp(42)));

        panel.addView(header);

        direction = makeText("🇺🇸 Norteamericano → 🇦🇷 Argentino",
                13, Color.rgb(255, 220, 90));
        direction.setGravity(Gravity.CENTER);
        direction.setPadding(0, dp(8), 0, dp(8));
        panel.addView(direction);
        direction.setOnClickListener(v -> switchDirection());

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(6), 0, dp(4), 0);
        inputRow.setBackground(rounded(Color.rgb(35, 55, 100)));

        input = new EditText(this);
        input.setHint("Escribí una palabra o frase");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setTextSize(15);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setBackgroundColor(Color.TRANSPARENT);
        inputRow.addView(input,
                new LinearLayout.LayoutParams(0, dp(58), 1));

        Button micButton = makeButton("🎙");
        inputRow.addView(micButton,
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);

        Button swapButton = makeButton("🇺🇸 ⇄ 🇦🇷");
        actions.addView(swapButton,
                new LinearLayout.LayoutParams(dp(112), dp(48)));

        Button translateButton = makeButton("Traducir");
        actions.addView(translateButton,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(actions);

        result = makeText("La traducción aparecerá aquí.",
                16, Color.WHITE);
        result.setPadding(dp(8), dp(12), dp(8), dp(8));
        panel.addView(result);

        LinearLayout output = new LinearLayout(this);
        output.setGravity(Gravity.CENTER_VERTICAL);

        Button speakButton = makeButton("🔊 Escuchar");
        output.addView(speakButton,
                new LinearLayout.LayoutParams(0, dp(48), 1));

        Button copyButton = makeButton("Copiar");
        output.addView(copyButton,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(output);

        TextView resizeHint = makeText(
                "🤏 Pellizcá para cambiar tamaño · Arrastrá arriba para mover",
                10, Color.LTGRAY);
        resizeHint.setPadding(0, dp(8), 0, 0);
        panel.addView(resizeHint);

        int windowType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(120);

        header.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int initialX, initialY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.max(0, Math.min(
                                screenWidth - panel.getWidth(),
                                initialX + (int) (event.getRawX() - downX)));
                        params.y = Math.max(0, Math.min(
                                screenHeight - dp(80),
                                initialY + (int) (event.getRawY() - downY)));
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
            output.setVisibility(minimized ? View.GONE : View.VISIBLE);
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
        swapButton.setOnClickListener(v -> switchDirection());
        translateButton.setOnClickListener(v -> translateText());
        micButton.setOnClickListener(v -> startVoiceRecognition());
        speakButton.setOnClickListener(v -> speakTranslation());

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "WordFlow", result.getText().toString()));
            Toast.makeText(this, "Texto copiado",
                    Toast.LENGTH_SHORT).show();
        });

        try {
            wm.addView(panel, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void switchDirection() {
        northToArgentine = !northToArgentine;
        direction.setText(northToArgentine
                ? "🇺🇸 Norteamericano → 🇦🇷 Argentino"
                : "🇦🇷 Argentino → 🇺🇸 Norteamericano");
        setSpeechLanguage();
    }

    private void startVoiceRecognition() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Autorizá el micrófono desde WordFlow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,
                    "El reconocimiento de voz no está disponible.",
                    Toast.LENGTH_SHORT).show();
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
                @Override public void onReadyForSpeech(android.os.Bundle b) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(android.os.Bundle b) {}
                @Override public void onEvent(int e, android.os.Bundle b) {}

                @Override
                public void onError(int error) {
                    handler.post(() -> Toast.makeText(
                            OverlayService.this,
                            "No pude reconocer la voz. Intentá otra vez.",
                            Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResults(android.os.Bundle b) {
                    ArrayList<String> matches = b.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);

                    if (matches != null && !matches.isEmpty()) {
                        handler.post(() -> {
                            input.setText(matches.get(0));
                            input.setSelection(input.length());
                        });
                    }
                }
            });

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speechRecognizer.startListening(intent);

        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar el micrófono.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void translateText() {
        String source = input.getText().toString().trim();

        if (source.isEmpty()) {
            result.setText("Escribí o dictá algo primero.");
            return;
        }

        result.setText("Traduciendo…");
        final boolean toArgentine = northToArgentine;

        worker.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded = URLEncoder.encode(source, "UTF-8");
                String pair = toArgentine ? "en|es" : "es|en";
                URL url = new URL(
                        "https://api.mymemory.translated.net/get?q="
                                + encoded + "&langpair=" + pair);

                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject json = new JSONObject(response.toString());
                String translated = json.getJSONObject("responseData")
                        .getString("translatedText");

                if (!toArgentine) {
                    translated = ArgentineSpanishAdapter
                            .toArgentineSpanish(translated);
                }

                final String finalText = translated;
                handler.post(() -> result.setText(finalText));

            } catch (Exception e) {
                handler.post(() -> result.setText(
                        "No se pudo traducir. Revisá internet e intentá otra vez."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) {
            tts.setLanguage(northToArgentine
                    ? new Locale("es", "AR")
                    : Locale.US);
        }
    }

    private void speakTranslation() {
        String value = res
