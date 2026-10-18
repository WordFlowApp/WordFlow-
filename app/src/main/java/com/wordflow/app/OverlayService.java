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
    private LinearLayout panel, directionRow;
    private EditText input;
    private TextView result, direction, sourceFlag, targetFlag;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ttsReady, northToArgentine = true, minimized;
    private int screenWidth, screenHeight;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
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
        String id = "wordflow_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(id, "WordFlow flotante", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, id) : new Notification.Builder(this);
        Notification n = b.setContentTitle("WordFlow está activo")
                .setContentText("Tu traductor flotante está disponible")
                .setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build();
        startForeground(7, n);
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(18));
        return d;
    }

    private GradientDrawable circularIcon(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(2), Color.rgb(65, 183, 255));
        return d;
    }

    private TextView makeText(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        return v;
    }

    private Button makeButton(String value) {
        Button b = new Button(this);
        b.setText(value); b.setAllCaps(false);
        return b;
    }

    private void showPanel() {
        panel = new LinearLayout(this) {
            float initialDistance;
            int initialWidth, initialHeight;
            boolean resizing;
            float distance(MotionEvent e) {
                if (e.getPointerCount() < 2) return 0;
                float x = e.getX(0) - e.getX(1), y = e.getY(0) - e.getY(1);
                return (float) Math.sqrt(x*x + y*y);
            }
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() >= 2) {
                    initialDistance = distance(e); initialWidth = params.width;
                    initialHeight = Math.max(getHeight(), dp(300)); resizing = true;
                    return true;
                }
                if (resizing && a == MotionEvent.ACTION_MOVE && e.getPointerCount() >= 2) {
                    float d = distance(e);
                    if (initialDistance > 0 && d > 0) {
                        float scale = d / initialDistance;
                        // Resize the width while allowing the panel height to
                        // wrap its content; a fixed height clipped the buttons.
                        int screenWidthDp = (int)(screenWidth / getResources().getDisplayMetrics().density);
                        int minWidth = dp(Math.max(190, screenWidthDp * 0.48f));
                        int maxWidth = dp(Math.max(220, screenWidthDp * 0.72f));
                        params.width = Math.max(minWidth, Math.min(maxWidth, (int)(initialWidth * scale)));
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                    }
                    return true;
                }
                if (resizing && (a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)) {
                    resizing = false; params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                    return true;
                }
                if (resizing) return true;
                return super.dispatchTouchEvent(e);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable panelBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(9, 49, 125), Color.rgb(4, 19, 54)});
        panelBg.setCornerRadius(dp(22));
        panelBg.setStroke(dp(2), Color.rgb(50, 157, 255));
        panel.setBackground(panelBg);
        panel.setElevation(dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(5), dp(4), dp(5));
        GradientDrawable headerBg = rounded(Color.rgb(20, 93, 190));
        header.setBackground(headerBg);
        TextView title = makeText("🌐  WordFlow", 23, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button minimizeButton = makeButton("−"), closeButton = makeButton("×");
        minimizeButton.setTextColor(Color.WHITE); minimizeButton.setTextSize(20);
        minimizeButton.setBackground(rounded(Color.rgb(35, 118, 218)));
        GradientDrawable minGlow = rounded(Color.rgb(35, 118, 218));
        minGlow.setStroke(dp(1), Color.rgb(72, 173, 255));
        minimizeButton.setBackground(minGlow);
        closeButton.setTextColor(Color.WHITE); closeButton.setTextSize(22);
        GradientDrawable closeGlow = rounded(Color.rgb(204, 55, 77));
        closeGlow.setStroke(dp(1), Color.rgb(255, 103, 124));
        closeButton.setBackground(closeGlow);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        minLp.leftMargin = dp(4); header.addView(minimizeButton, minLp);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeLp.leftMargin = dp(4); header.addView(closeButton, closeLp);
        panel.addView(header);

        directionRow = new LinearLayout(this);
        directionRow.setGravity(Gravity.CENTER);
        directionRow.setPadding(dp(6), dp(8), dp(6), dp(8));
        directionRow.setBackground(rounded(Color.rgb(15, 39, 88)));
        sourceFlag = makeText("🇺🇸", 18, Color.WHITE);
        sourceFlag.setGravity(Gravity.CENTER);
        sourceFlag.setBackground(circularIcon(Color.rgb(18, 55, 120)));
        direction = makeText("NORTEAMERICANO  ➜  ARGENTINO", 11, Color.WHITE);
        direction.setTypeface(null, Typeface.BOLD);
        direction.setGravity(Gravity.CENTER);
        direction.setPadding(dp(8), dp(4), dp(8), dp(4));
        targetFlag = makeText("🇦🇷", 18, Color.WHITE);
        targetFlag.setGravity(Gravity.CENTER);
        targetFlag.setBackground(circularIcon(Color.rgb(18, 55, 120)));
        int flagSize = dp(42);
        directionRow.addView(sourceFlag, new LinearLayout.LayoutParams(flagSize, flagSize));
        directionRow.addView(direction, new LinearLayout.LayoutParams(0, -2, 1));
        directionRow.addView(targetFlag, new LinearLayout.LayoutParams(flagSize, flagSize));
        panel.addView(directionRow);
        directionRow.setOnClickListener(v -> switchDirection());
        sourceFlag.setOnClickListener(v -> switchDirection());
        targetFlag.setOnClickListener(v -> switchDirection());
        direction.setOnClickListener(v -> switchDirection());

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL); inputRow.setPadding(dp(8), 0, dp(6), 0);
        GradientDrawable inputBg = rounded(Color.rgb(10, 30, 68));
        inputBg.setStroke(dp(1), Color.rgb(75, 167, 255));
        inputRow.setBackground(inputBg);
        input = new EditText(this); input.setHint("Escribí una palabra o frase");
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY);
        input.setTextSize(15); input.setMinLines(1); input.setMaxLines(3);
        input.setPadding(dp(8), dp(6), dp(4), dp(6));
        input.setBackgroundColor(Color.TRANSPARENT);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1));
        Button mic = makeButton("🎙");
        mic.setTextColor(Color.WHITE); mic.setTextSize(17);
        GradientDrawable micGlow = rounded(Color.rgb(41, 112, 218));
        micGlow.setCornerRadius(dp(24));
        micGlow.setStroke(dp(1), Color.rgb(70, 172, 255));
        mic.setBackground(micGlow);
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        Button swap = makeButton("⇄"), translate = makeButton("✦  TRADUCIR");
        swap.setTextColor(Color.WHITE);
        GradientDrawable swapBg = circularIcon(Color.rgb(27, 63, 124));
        swap.setTextSize(23);
        swap.setBackground(swapBg);
        translate.setTextColor(Color.WHITE); translate.setTypeface(null, Typeface.BOLD);
        GradientDrawable translateBg = rounded(Color.rgb(0, 132, 255));
        translateBg.setStroke(dp(1), Color.rgb(65, 183, 255));
        translate.setBackground(translateBg);
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(48), dp(46)); swapLp.topMargin = dp(8);
        actions.addView(swap, swapLp);
        LinearLayout.LayoutParams translateLp = new LinearLayout.LayoutParams(0, dp(46), 1); translateLp.leftMargin = dp(8); translateLp.topMargin = dp(8);
        actions.addView(translate, translateLp); panel.addView(actions);
        result = makeText("La traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(14), dp(16), dp(14), dp(14));
        GradientDrawable resultBg = rounded(Color.rgb(12, 35, 79));
        resultBg.setStroke(dp(1), Color.rgb(47, 126, 226));
        result.setBackground(resultBg);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, -2); resultLp.topMargin = dp(10);
        panel.addView(result, resultLp);
        LinearLayout output = new LinearLayout(this); output.setGravity(Gravity.CENTER_VERTICAL);
        Button speak = makeButton("🔊 Escuchar"), copy = makeButton("Copiar");
        speak.setTextColor(Color.WHITE);
        GradientDrawable speakBg = rounded(Color.rgb(27, 63, 124));
        speakBg.setStroke(dp(1), Color.rgb(45, 109, 190)); speak.setBackground(speakBg);
        copy.setTextColor(Color.WHITE);
        GradientDrawable copyBg = rounded(Color.rgb(27, 63, 124));
        copyBg.setStroke(dp(1), Color.rgb(45, 109, 190)); copy.setBackground(copyBg);
        LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(0, dp(44), 1); speakLp.topMargin = dp(8);
        output.addView(speak, speakLp);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(44), 1); copyLp.leftMargin = dp(8); copyLp.topMargin = dp(8);
        output.addView(copy, copyLp); panel.addView(output);
        TextView hint = makeText("⠿  ARRASTRÁ PARA MOVER   ·   🤏  PELLIZCÁ PARA AJUSTAR", 9, Color.rgb(184, 216, 255));
        hint.setPadding(0, dp(8), 0, 0); panel.addView(hint);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        // Start compact: target about one quarter of the phone's visible area.
        // Width is responsive to screen size and deliberately capped so the overlay
        // does not cover the video like the previous near-half-screen layout.
        int screenWidthDp = (int)(screenWidth / getResources().getDisplayMetrics().density);
        int initialWidthDp = Math.max(210, Math.min(260, (int)(screenWidthDp * 0.62f)));
        params = new WindowManager.LayoutParams(dp(initialWidthDp), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(8); params.y = dp(96);

        header.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); startX = params.x; startY = params.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.max(0, Math.min(screenWidth - panel.getWidth(), startX + (int)(e.getRawX()-downX)));
                        params.y = Math.max(0, Math.min(screenHeight - dp(80), startY + (int)(e.getRawY()-downY)));
                        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                    default: return false;
                }
            }
        });
        minimizeButton.setOnClickListener(v -> {
            minimized = !minimized;
            inputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            actions.setVisibility(minimized ? View.GONE : View.VISIBLE);
            result.setVisibility(minimized ? View.GONE : View.VISIBLE);
            output.setVisibility(minimized ? View.GONE : View.VISIBLE);
            hint.setVisibility(minimized ? View.GONE : View.VISIBLE);
            directionRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimizeButton.setText(minimized ? "+" : "—");
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        });
        closeButton.setOnClickListener(v -> stopSelf());
        swap.setOnClickListener(v -> switchDirection());
        translate.setOnClickListener(v -> translateText());
        mic.setOnClickListener(v -> startVoiceRecognition());
        speak.setOnClickListener(v -> speakTranslation());
        copy.setOnClickListener(v -> {
            String value = result.getText().toString().trim();
            if (value.isEmpty() || value.equals("La traducción aparecerá aquí.")
                    || value.equals("Traduciendo…")
                    || value.equals("Escribí o dictá algo primero.")
                    || value.startsWith("No se pudo traducir.")) {
                Toast.makeText(this, "Primero obtené una traducción.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cb = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("WordFlow", value));
            Toast.makeText(this, "Traducción copiada", Toast.LENGTH_SHORT).show();
        });
        try { wm.addView(panel, params); } catch (Exception e) { stopSelf(); }
    }

    private void switchDirection() {
        northToArgentine = !northToArgentine;
        direction.setText(northToArgentine ? "NORTEAMERICANO  ➜  ARGENTINO" : "ARGENTINO  ➜  NORTEAMERICANO");
        sourceFlag.setText(northToArgentine ? "🇺🇸" : "🇦🇷");
        targetFlag.setText(northToArgentine ? "🇦🇷" : "🇺🇸");
        setSpeechLanguage();
    }

    private void startVoiceRecognition() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorizá el micrófono desde WordFlow.", Toast.LENGTH_LONG).show(); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconocimiento de voz no disponible.", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            if (recognizer != null) recognizer.destroy();
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle b) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(android.os.Bundle b) { }
                @Override public void onEvent(int e, android.os.Bundle b) { }
                @Override public void onError(int error) {
                    main.post(() -> Toast.makeText(OverlayService.this, "No reconocí la voz. Intentá otra vez.", Toast.LENGTH_SHORT).show());
                }
                @Override public void onResults(android.os.Bundle b) {
                    ArrayList<String> matches = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) main.post(() -> {
                        input.setText(matches.get(0)); input.setSelection(input.length());
                    });
                }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar el micrófono.", Toast.LENGTH_SHORT).show();
        }
    }

    private void translateText() {
        String source = input.getText().toString().trim();
        if (source.isEmpty()) { result.setText("Escribí o dictá algo primero."); return; }
        result.setText("Traduciendo…");
        final boolean toArgentine = northToArgentine;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String pair = toArgentine ? "en|es" : "es|en";
                String encoded = URLEncoder.encode(source, "UTF-8");
                URL url = new URL("https://api.mymemory.translated.net/get?q=" + encoded + "&langpair=" + pair);
                connection = (HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                String translated = json.getJSONObject("responseData").getString("translatedText");
                translated = android.text.Html.fromHtml(translated, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                final String finalText = toArgentine ? localizeArgentineSpanish(source, translated) : translated;
                main.post(() -> result.setText(finalText));
            } catch (Exception e) {
                main.post(() -> result.setText("No se pudo traducir. Revisá internet e intentá otra vez."));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    // Conservative phrase overrides keyed to the original English input.
    // Generic translated Spanish is never searched for isolated English words:
    // that could corrupt words such as "homework" or change the intended meaning.
    private String localizeArgentineSpanish(String source, String translated) {
        if (source == null || translated == null) return translated;
        String normalized = source.trim().toLowerCase(Locale.US)
                .replaceAll("[.!?]+$", "").trim();
        switch (normalized) {
            case "how are you":
            case "how are you doing":
                return "¿Cómo andás?";
            case "what's up":
            case "whats up":
                return "¿Qué onda?";
            case "what are you doing":
                return "¿Qué estás haciendo?";
            case "no problem":
                return "No hay drama.";
            case "let's go":
            case "lets go":
                return "Dale, vamos.";
            case "come on":
                return "Dale.";
            case "that's awesome":
            case "thats awesome":
            case "that's cool":
            case "thats cool":
                return "¡Está buenísimo!";
            case "i don't care":
            case "i dont care":
                return "Me da lo mismo.";
            case "wait a minute":
            case "give me a second":
                return "Bancame un segundo.";
            case "i'm broke":
            case "im broke":
                return "Estoy sin un mango.";
            case "money":
                return "Plata";
            case "work":
                return "Laburo";
            case "cool":
                return "Copado";
            case "okay":
            case "ok":
                return "Dale";
            default:
                return translated;
        }
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) tts.setLanguage(northToArgentine ? new Locale("es", "AR") : Locale.US);
    }

    private void speakTranslation() {
        String value = result.getText().toString().trim();
        if (value.isEmpty() || value.equals("La traducción aparecerá aquí.")
                || value.equals("Traduciendo…") || value.equals("Escribí o dictá algo primero.")
                || value.startsWith("No se pudo traducir.")) {
            Toast.makeText(this, "Primero traducí un texto.", Toast.LENGTH_SHORT).show(); return;
        }
        if (ttsReady && tts != null) {
            setSpeechLanguage(); tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "wordflow_speech");
        } else Toast.makeText(this, "El audio todavía no está listo.", Toast.LENGTH_SHORT).show();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (panel != null && params != null && wm != null) {
            params.x = Math.max(0, Math.min(params.x, screenWidth - panel.getWidth()));
            params.y = Math.max(0, Math.min(params.y, screenHeight - dp(80)));
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (panel != null && wm != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
