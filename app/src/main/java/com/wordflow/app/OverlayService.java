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
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
    private TextView result;
    private LinearLayout direction;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ttsReady, northToArgentine = true, minimized;
    private int screenWidth, screenHeight;
    private float appliedUiScale = 1f;
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
            float initialUiScale;
            int initialWidth;
            boolean resizing;
            float distance(MotionEvent e) {
                if (e.getPointerCount() < 2) return 0;
                float x = e.getX(0) - e.getX(1), y = e.getY(0) - e.getY(1);
                return (float) Math.sqrt(x*x + y*y);
            }
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() >= 2) {
                    initialDistance = distance(e);
                    initialWidth = params.width;
                    initialUiScale = appliedUiScale;
                    resizing = initialDistance > 0;
                    return resizing;
                }
                if (resizing && a == MotionEvent.ACTION_MOVE && e.getPointerCount() >= 2) {
                    float d = distance(e);
                    if (initialDistance > 0 && d > 0) {
                        float requested = Math.max(0.72f, Math.min(1.38f,
                                initialUiScale * d / initialDistance));
                        float factor = requested / appliedUiScale;
                        if (Math.abs(factor - 1f) > 0.008f) {
                            scaleChildViews(panel, factor);
                            appliedUiScale = requested;
                            params.width = Math.max(dp(250), Math.min(screenWidth - dp(16),
                                    Math.round(initialWidth * requested / initialUiScale)));
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        }
                    }
                    return true;
                }
                if (resizing && (a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)) {
                    resizing = false;
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
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
        SpannableString brand = new SpannableString("WordFlow");
        brand.setSpan(new ForegroundColorSpan(Color.rgb(0, 225, 255)), 4, 8,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = makeText(brand.toString(), 21, Color.WHITE);
        title.setText(brand);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button minimizeButton = makeButton("−"), closeButton = makeButton("×"), menuButton = makeButton("⋮");
        for (Button b : new Button[]{minimizeButton, closeButton, menuButton}) {
            b.setTextColor(Color.WHITE);
            b.setBackground(rounded(Color.rgb(8, 45, 105)));
            b.setSoundEffectsEnabled(false);
        }
        minimizeButton.setTextSize(22);
        closeButton.setTextSize(22);
        menuButton.setTextSize(24);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        minLp.leftMargin = dp(4); header.addView(minimizeButton, minLp);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeLp.leftMargin = dp(4); header.addView(closeButton, closeLp);
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        menuLp.leftMargin = dp(4); header.addView(menuButton, menuLp);
        panel.addView(header);

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
        mic.setBackground(rounded(Color.rgb(41, 112, 218)));
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        direction = new LinearLayout(this);
        direction.setGravity(Gravity.CENTER);
        direction.setOrientation(LinearLayout.HORIZONTAL);
        direction.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable languageBg = rounded(Color.rgb(7, 28, 68));
        languageBg.setStroke(dp(2), Color.rgb(0, 220, 255));
        direction.setBackground(languageBg);
        Button translate = makeButton("✦  TRADUCIR");
        translate.setTextColor(Color.rgb(4, 24, 65));
        translate.setTypeface(null, Typeface.BOLD);
        translate.setTextSize(17);
        GradientDrawable translateBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(0, 229, 238), Color.rgb(0, 151, 255)});
        translateBg.setCornerRadius(dp(34));
        translate.setBackground(translateBg);
        translate.setSoundEffectsEnabled(false);
        LinearLayout.LayoutParams languageLp = new LinearLayout.LayoutParams(0, dp(68), 1.05f);
        languageLp.topMargin = dp(8);
        actions.addView(direction, languageLp);
        LinearLayout.LayoutParams translateLp = new LinearLayout.LayoutParams(0, dp(68), 1.2f);
        translateLp.leftMargin = dp(8); translateLp.topMargin = dp(8);
        actions.addView(translate, translateLp);
        panel.addView(actions);
        updateDirection();
        result = makeText("¡La traducción aparece acá!\n\nLa traducción aparecerá aquí.", 16, Color.WHITE);
        result.setPadding(dp(14), dp(16), dp(14), dp(14));
        GradientDrawable resultBg = rounded(Color.rgb(12, 35, 79));
        resultBg.setStroke(dp(1), Color.rgb(47, 126, 226));
        result.setBackground(resultBg);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, -2); resultLp.topMargin = dp(10);
        panel.addView(result, resultLp);
        LinearLayout output = new LinearLayout(this); output.setGravity(Gravity.CENTER_VERTICAL);
        Button speak = makeButton("◖))"), copy = makeButton("⧉");
        speak.setContentDescription("Escuchar traducción");
        copy.setContentDescription("Copiar traducción");
        speak.setTextSize(22);
        copy.setTextSize(26);
        speak.setTextColor(Color.WHITE); speak.setBackground(rounded(Color.rgb(0, 143, 255)));
        copy.setTextColor(Color.WHITE); copy.setBackground(rounded(Color.rgb(8, 45, 105)));
        speak.setSoundEffectsEnabled(false);
        copy.setSoundEffectsEnabled(false);
        LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(0, dp(44), 1); speakLp.topMargin = dp(8);
        output.addView(speak, speakLp);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(44), 1); copyLp.leftMargin = dp(8); copyLp.topMargin = dp(8);
        output.addView(copy, copyLp); panel.addView(output);
        TextView hint = makeText("⠿  ARRASTRÁ PARA MOVER   ·   🤏  PELLIZCÁ PARA AJUSTAR", 9, Color.rgb(184, 216, 255));
        hint.setPadding(0, dp(8), 0, 0); panel.addView(hint);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(320), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(12); params.y = dp(120);

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
            direction.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimizeButton.setText(minimized ? "+" : "—");
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        });
        closeButton.setOnClickListener(v -> stopSelf());
        menuButton.setOnClickListener(v ->
                Toast.makeText(this, "WordFlow · Arrastrá la barra para mover y pellizcá para ajustar.", Toast.LENGTH_LONG).show());
        translate.setOnClickListener(v -> translateText());
        mic.setContentDescription("Dictar con el micrófono");
        mic.setSoundEffectsEnabled(false);
        mic.setOnClickListener(v -> startVoiceRecognition());
        speak.setOnClickListener(v -> speakTranslation());
        copy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("WordFlow", result.getText().toString()));
            Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
        });
        try { wm.addView(panel, params); } catch (Exception e) { stopSelf(); }
    }

    private void switchDirection() {
        northToArgentine = !northToArgentine;
        updateDirection();
        setSpeechLanguage();
    }

    private Button circularLanguageButton(String label, int color, int size) {
        Button b = makeButton(label);
        b.setTextSize(size);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(color));
        b.setSoundEffectsEnabled(false);
        return b;
    }

    private void updateDirection() {
        if (direction == null) return;
        direction.removeAllViews();
        Button argentina = circularLanguageButton("🇦🇷", Color.rgb(8, 75, 151), 24);
        Button usa = circularLanguageButton("🇺🇸", Color.rgb(8, 75, 151), 24);
        Button right = circularLanguageButton("➜", Color.rgb(255, 205, 0), 23);
        right.setTextColor(Color.rgb(7, 35, 80));
        Button left = circularLanguageButton("←", Color.rgb(8, 75, 151), 21);
        LinearLayout arrows = new LinearLayout(this);
        arrows.setOrientation(LinearLayout.VERTICAL);
        arrows.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(36), dp(34));
        arrows.addView(right, arrowLp);
        LinearLayout.LayoutParams arrowLp2 = new LinearLayout.LayoutParams(dp(36), dp(30));
        arrowLp2.topMargin = dp(2);
        arrows.addView(left, arrowLp2);
        LinearLayout.LayoutParams flagLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        LinearLayout.LayoutParams arrowsLp = new LinearLayout.LayoutParams(dp(38), dp(66));
        direction.addView(argentina, flagLp);
        direction.addView(arrows, arrowsLp);
        direction.addView(usa, new LinearLayout.LayoutParams(dp(48), dp(48)));
        argentina.setOnClickListener(v -> { if (!northToArgentine) switchDirection(); });
        usa.setOnClickListener(v -> { if (northToArgentine) switchDirection(); });
        right.setOnClickListener(v -> { if (!northToArgentine) switchDirection(); });
        left.setOnClickListener(v -> { if (northToArgentine) switchDirection(); });
        argentina.setAlpha(northToArgentine ? 1f : 0.75f);
        usa.setAlpha(northToArgentine ? 1f : 0.75f);
    }

    private void scaleChildViews(View view, float factor) {
        if (view == panel) {
            view.setPadding(Math.max(dp(6), Math.round(view.getPaddingLeft() * factor)),
                    Math.max(dp(5), Math.round(view.getPaddingTop() * factor)),
                    Math.max(dp(6), Math.round(view.getPaddingRight() * factor)),
                    Math.max(dp(6), Math.round(view.getPaddingBottom() * factor)));
        } else {
            view.setPadding(Math.max(0, Math.round(view.getPaddingLeft() * factor)),
                    Math.max(0, Math.round(view.getPaddingTop() * factor)),
                    Math.max(0, Math.round(view.getPaddingRight() * factor)),
                    Math.max(0, Math.round(view.getPaddingBottom() * factor)));
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextSize(Math.max(9f, Math.min(30f, tv.getTextSize() /
                    getResources().getDisplayMetrics().scaledDensity * factor)));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                android.view.ViewGroup.LayoutParams raw = child.getLayoutParams();
                if (raw != null) {
                    if (raw.width > 0) raw.width = Math.max(dp(20), Math.round(raw.width * factor));
                    if (raw.height > 0) raw.height = Math.max(dp(20), Math.round(raw.height * factor));
                    if (raw instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                        lp.leftMargin = Math.max(0, Math.round(lp.leftMargin * factor));
                        lp.rightMargin = Math.max(0, Math.round(lp.rightMargin * factor));
                        lp.topMargin = Math.max(0, Math.round(lp.topMargin * factor));
                        lp.bottomMargin = Math.max(0, Math.round(lp.bottomMargin * factor));
                    }
                    child.setLayoutParams(raw);
                }
                scaleChildViews(child, factor);
            }
        }
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
            Toast.makeText(this, "Te escucho…", Toast.LENGTH_SHORT).show();
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
                final String finalText = translated;
                main.post(() -> result.setText(finalText));
            } catch (Exception e) {
                main.post(() -> result.setText("No se pudo traducir. Revisá internet e intentá otra vez."));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) tts.setLanguage(northToArgentine ? new Locale("es", "AR") : Locale.US);
    }

    private void speakTranslation() {
        String value = result.getText().toString().trim();
        if (value.isEmpty() || value.equals("La traducción aparecerá aquí.") || value.equals("Traduciendo…")) {
            Toast.makeText(this, "Primero traducí un texto.", Toast.LENGTH_SHORT).show(); return;
        }
        if (ttsReady && tts != null) {
            setSpeechLanguage(); tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "wordflow_speech");
        } else Toast.makeText(this, "El audio todavía no está listo.", Toast.LENGTH_SHORT).show();
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
