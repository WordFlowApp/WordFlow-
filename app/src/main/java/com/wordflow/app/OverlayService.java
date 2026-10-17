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
import android.graphics.Typeface;
import android.graphics.PixelFormat;
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
import android.view.ViewConfiguration;
import android.view.View;
import android.view.WindowManager;
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
    private static final int NAVY = Color.rgb(5, 20, 53);
    private static final int DEEP_BLUE = Color.rgb(8, 34, 82);
    private static final int BLUE = Color.rgb(0, 112, 255);
    private static final int CYAN = Color.rgb(0, 224, 255);
    private static final int LIGHT_BLUE = Color.rgb(70, 158, 255);

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout panel, inputRow, actionRow, outputRow;
    private EditText input;
    private TextView result, minimize, sourceFlag, targetFlag, swap;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ttsReady, northToArgentine = true, minimized;
    private int screenWidth, screenHeight;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private class FloatingPanel extends LinearLayout {
        private float pinchStartDistance;
        private int pinchStartWidth;
        private boolean pinching;

        FloatingPanel(Context context) { super(context); }

        private float distance(MotionEvent e) {
            if (e.getPointerCount() < 2) return 0f;
            float dx = e.getX(0) - e.getX(1);
            float dy = e.getY(0) - e.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getPointerCount() >= 2 || pinching) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    pinchStartDistance = distance(event);
                    pinchStartWidth = params != null && params.width > 0 ? params.width : getWidth();
                    pinching = true;
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE && pinching) {
                    float current = distance(event);
                    if (pinchStartDistance > 0f && current > 0f && params != null) {
                        float factor = current / pinchStartDistance;
                        int minWidth = dp(285);
                        int maxWidth = screenWidth - dp(16);
                        params.width = Math.max(minWidth, Math.min(maxWidth, (int)(pinchStartWidth * factor)));
                        try { wm.updateViewLayout(this, params); } catch (Exception ignored) { }
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pinching = false;
                    return true;
                }
                if (pinching) return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }

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
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, id) : new Notification.Builder(this);
        Notification notification = builder.setContentTitle("WordFlow está activo")
                .setContentText("Tu traductor flotante está disponible")
                .setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build();
        startForeground(7, notification);
    }

    private GradientDrawable shape(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private GradientDrawable gradient(int top, int bottom, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(2), strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private TextView pillButton(String label, int size, int background, int foreground) {
        TextView v = text(label, size, foreground, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(shape(background, 24, background, 1));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void showPanel() {
        panel = new FloatingPanel(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(9), dp(10), dp(9));
        panel.setBackground(gradient(Color.rgb(7, 43, 108), NAVY, 24, LIGHT_BLUE));
        panel.setElevation(dp(14));

        // Header: drag the window by touching and moving this bar.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(4), dp(4), dp(4));
        header.setBackground(shape(Color.rgb(11, 79, 174), 22, Color.rgb(20, 123, 255), 1));

        TextView brand = text("Word", 23, Color.WHITE, true);
        TextView flow = text("Flow", 23, CYAN, true);
        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.addView(brand);
        logo.addView(flow);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));

        minimize = pillButton("−", 24, Color.rgb(7, 49, 110), CYAN);
        TextView expand = pillButton("↗", 20, Color.rgb(7, 49, 110), Color.WHITE);
        TextView close = pillButton("×", 25, Color.rgb(7, 49, 110), Color.WHITE);
        TextView more = pillButton("⋮", 22, Color.rgb(7, 49, 110), Color.WHITE);
        header.addView(minimize, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout.LayoutParams expandLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        expandLp.leftMargin = dp(4);
        header.addView(expand, expandLp);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        closeLp.leftMargin = dp(4);
        header.addView(close, closeLp);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(28), dp(36));
        moreLp.leftMargin = dp(2);
        header.addView(more, moreLp);
        panel.addView(header);

        // Source text field with US flag and microphone.
        inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(7), dp(4), dp(5), dp(4));
        inputRow.setBackground(shape(Color.rgb(7, 27, 64), 22, LIGHT_BLUE, 1));

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(2);
        input.setTextSize(16);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(190, 205, 230));
        input.setHint("Escribí una palabra o frase…");
        input.setPadding(dp(8), dp(4), dp(4), dp(4));
        input.setBackgroundColor(Color.TRANSPARENT);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView mic = pillButton("🎙", 20, BLUE, Color.WHITE);
        mic.setContentDescription("Dictar por voz");
        inputRow.addView(mic, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.topMargin = dp(8);
        panel.addView(inputRow, inputLp);

        actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(2), 0, dp(2), 0);

        LinearLayout languages = new LinearLayout(this);
        languages.setGravity(Gravity.CENTER);
        languages.setBackground(shape(Color.rgb(4, 25, 62), 28, CYAN, 2));
        sourceFlag = text("🇺🇸", 28, Color.WHITE, false);
        sourceFlag.setGravity(Gravity.CENTER);
        sourceFlag.setBackground(gradient(Color.rgb(0, 126, 255), Color.rgb(4, 35, 91), 40, CYAN));
        languages.addView(sourceFlag, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout arrows = new LinearLayout(this);
        arrows.setOrientation(LinearLayout.VERTICAL);
        arrows.setGravity(Gravity.CENTER);
        TextView forward = text("➜", 21, Color.rgb(255, 205, 0), true);
        TextView backward = text("⬅", 21, CYAN, true);
        forward.setGravity(Gravity.CENTER);
        backward.setGravity(Gravity.CENTER);
        forward.setBackground(gradient(Color.rgb(5, 53, 119), Color.rgb(4, 28, 69), 30, Color.rgb(255, 205, 0)));
        backward.setBackground(gradient(Color.rgb(5, 53, 119), Color.rgb(4, 28, 69), 30, CYAN));
        LinearLayout.LayoutParams arrowLp1 = new LinearLayout.LayoutParams(dp(32), dp(32));
        arrowLp1.bottomMargin = dp(2);
        arrows.addView(forward, arrowLp1);
        arrows.addView(backward, new LinearLayout.LayoutParams(dp(32), dp(32)));
        languages.addView(arrows);
        targetFlag = text("🇦🇷", 28, Color.WHITE, false);
        targetFlag.setGravity(Gravity.CENTER);
        targetFlag.setBackground(gradient(Color.rgb(0, 126, 255), Color.rgb(4, 35, 91), 40, CYAN));
        languages.addView(targetFlag, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView translate = pillButton("✦  TRADUCIR", 20, Color.rgb(0, 213, 245), NAVY);
        translate.setBackground(gradient(Color.rgb(0, 239, 245), Color.rgb(0, 143, 255), 28, CYAN));
        LinearLayout.LayoutParams languagesLp = new LinearLayout.LayoutParams(0, dp(74), 1.0f);
        languagesLp.topMargin = dp(8);
        actionRow.addView(languages, languagesLp);
        LinearLayout.LayoutParams translateLp = new LinearLayout.LayoutParams(0, dp(74), 1.15f);
        translateLp.leftMargin = dp(7);
        translateLp.topMargin = dp(8);
        actionRow.addView(translate, translateLp);
        panel.addView(actionRow);

        // Translation field and action icons share one row, matching the approved design.
        outputRow = new LinearLayout(this);
        outputRow.setGravity(Gravity.CENTER_VERTICAL);
        outputRow.setPadding(dp(8), dp(4), dp(4), dp(4));
        outputRow.setBackground(shape(Color.rgb(4, 23, 56), 24, LIGHT_BLUE, 2));

        result = text("Aquí aparecerá la traducción…", 16, Color.WHITE, false);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(dp(7), dp(8), dp(5), dp(8));
        outputRow.addView(result, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView speak = pillButton("◖))", 21, Color.rgb(0, 115, 255), Color.WHITE);
        speak.setBackground(gradient(Color.rgb(0, 220, 255), Color.rgb(0, 73, 255), 30, CYAN));
        outputRow.addView(speak, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView copy = pillButton("▢", 25, Color.rgb(5, 40, 91), Color.WHITE);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        copyLp.leftMargin = dp(6);
        outputRow.addView(copy, copyLp);
        LinearLayout.LayoutParams outputLp = new LinearLayout.LayoutParams(-1, -2);
        outputLp.topMargin = dp(9);
        panel.addView(outputRow, outputLp);

        TextView hint = text("⠿  ARRASTRÁ PARA MOVER  ·  🤏  PELLIZCÁ PARA AJUSTAR", 9, Color.rgb(184, 216, 255), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(7), 0, dp(1));
        panel.addView(hint);

        params = new WindowManager.LayoutParams(
                Math.min(dp(350), screenWidth - dp(16)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(100);

        header.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = params.x; startY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.max(0, Math.min(screenWidth - panel.getWidth(), startX + (int)(e.getRawX() - downX)));
                        params.y = Math.max(0, Math.min(screenHeight - dp(100), startY + (int)(e.getRawY() - downY)));
                        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        return true;
                    default: return true;
                }
            }
        });

        minimize.setOnClickListener(v -> {
            minimized = !minimized;
            inputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            actionRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            outputRow.setVisibility(minimized ? View.GONE : View.VISIBLE);
            hint.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimize.setText(minimized ? "+" : "−");
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        });
        expand.setOnClickListener(v -> {
            params.width = params.width < screenWidth - dp(16) ? screenWidth - dp(16) : dp(320);
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        });
        more.setOnClickListener(v -> Toast.makeText(this, "WordFlow · Norteamericano ↔ Argentino", Toast.LENGTH_SHORT).show());
        close.setOnClickListener(v -> stopSelf());
        languages.setOnClickListener(v -> switchDirection());
        translate.setOnClickListener(v -> translateText());
        mic.setOnClickListener(v -> startVoiceRecognition());
        speak.setOnClickListener(v -> speakTranslation());
        copy.setOnClickListener(v -> {
            String value = result.getText().toString().trim();
            if (value.isEmpty() || value.startsWith("La traducción")) {
                Toast.makeText(this, "Primero traducí un texto.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("WordFlow", value));
            Toast.makeText(this, "Traducción copiada", Toast.LENGTH_SHORT).show();
        });

        try { wm.addView(panel, params); }
        catch (Exception e) { Toast.makeText(this, "No se pudo abrir la ventana flotante.", Toast.LENGTH_LONG).show(); stopSelf(); }
    }

    private void switchDirection() {
        northToArgentine = !northToArgentine;
        sourceFlag.setText(northToArgentine ? "🇺🇸" : "🇦🇷");
        targetFlag.setText(northToArgentine ? "🇦🇷" : "🇺🇸");
        input.setHint(northToArgentine ? "Escribí una palabra o frase…" : "Escribí una palabra o frase…");
        setSpeechLanguage();
    }

    private void startVoiceRecognition() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Activá el permiso Micrófono para WordFlow en Ajustes de Android y volvé a tocar el micrófono.", Toast.LENGTH_LONG).show();
            try {
                Intent settings = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
            } catch (Exception ignored) { }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Android no tiene un servicio de reconocimiento de voz disponible. Revisá Google app y el servicio de voz.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
                recognizer = null;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(OverlayService.this);
            Toast.makeText(this, "Micrófono activado: hablá ahora.", Toast.LENGTH_SHORT).show();
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle b) {
                    main.post(() -> Toast.makeText(OverlayService.this, "Te escucho… hablá ahora.", Toast.LENGTH_SHORT).show());
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(android.os.Bundle b) { }
                @Override public void onEvent(int e, android.os.Bundle b) { }
                @Override public void onError(int error) {
                    String reason;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO: reason = "Error de audio"; break;
                        case SpeechRecognizer.ERROR_CLIENT: reason = "Error del servicio de voz"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: reason = "Falta permiso de micrófono"; break;
                        case SpeechRecognizer.ERROR_NETWORK: reason = "Error de conexión"; break;
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: reason = "Se agotó el tiempo de conexión"; break;
                        case SpeechRecognizer.ERROR_NO_MATCH: reason = "No detecté palabras"; break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: reason = "El reconocimiento está ocupado"; break;
                        case SpeechRecognizer.ERROR_SERVER: reason = "Falló el servicio de voz"; break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: reason = "No detecté voz; hablá enseguida de tocar el micrófono"; break;
                        default: reason = "Error de reconocimiento";
                    }
                    final String message = reason;
                    main.post(() -> Toast.makeText(OverlayService.this, message, Toast.LENGTH_LONG).show());
                }
                @Override public void onResults(android.os.Bundle b) {
                    ArrayList<String> matches = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) main.post(() -> {
                        input.setText(matches.get(0));
                        input.setSelection(input.length());
                        Toast.makeText(OverlayService.this, "Voz convertida a texto", Toast.LENGTH_SHORT).show();
                    });
                    else main.post(() -> Toast.makeText(OverlayService.this, "No se reconocieron palabras. Probá hablar más cerca del micrófono.", Toast.LENGTH_LONG).show());
                }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, northToArgentine ? "en-US" : "es-AR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            recognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar el micrófono: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void translateText() {
        String source = input.getText().toString().trim();
        if (source.isEmpty()) {
            result.setText("Escribí o dictá una palabra o frase.");
            return;
        }
        result.setText("Traduciendo…");
        final boolean toArgentine = northToArgentine;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String pair = toArgentine ? "en|es" : "es|en";
                String encoded = URLEncoder.encode(source, "UTF-8");
                URL url = new URL("https://api.mymemory.translated.net/get?q=" + encoded + "&langpair=" + pair);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                String translated = json.getJSONObject("responseData").getString("translatedText");
                String safeText = translated.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
                main.post(() -> result.setText(safeText));
            } catch (Exception e) {
                main.post(() -> result.setText("No se pudo traducir. Revisá internet e intentá otra vez."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) {
            tts.setLanguage(northToArgentine ? new Locale("es", "AR") : Locale.US);
        }
    }

    private void speakTranslation() {
        String value = result.getText().toString().trim();
        if (value.isEmpty() || value.startsWith("La traducción") || value.equals("Traduciendo…") || value.startsWith("No se pudo traducir")) {
            Toast.makeText(this, "Primero traducí un texto.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ttsReady && tts != null) {
            setSpeechLanguage();
            tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "wordflow_speech");
        } else {
            Toast.makeText(this, "El audio todavía no está listo.", Toast.LENGTH_SHORT).show();
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

    @Override public IBinder onBind(Intent intent) { return null; }
}
