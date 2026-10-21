package com.wordflow.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.AlertDialog;
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
    private boolean ttsReady, minimized;
    private Language sourceLanguage = LANGUAGES[0];
    private Language targetLanguage = LANGUAGES[1];

    private static final class Language {
        final String name, flag, displayCode, localeTag, apiCode;
        Language(String name, String flag, String displayCode, String localeTag, String apiCode) {
            this.name = name; this.flag = flag; this.displayCode = displayCode;
            this.localeTag = localeTag; this.apiCode = apiCode;
        }
        String label() { return flag + " " + displayCode; }
    }

    // 18 languages, with Argentine Spanish as the default destination.
    private static final Language[] LANGUAGES = new Language[] {
        new Language("English (United States)", "🇺🇸", "USA", "en-US", "en"),
        new Language("Español argentino", "🇦🇷", "ARG", "es-AR", "es"),
        new Language("Português (Brasil)", "🇧🇷", "BRA", "pt-BR", "pt"),
        new Language("Français", "🇫🇷", "FRA", "fr-FR", "fr"),
        new Language("Deutsch", "🇩🇪", "DEU", "de-DE", "de"),
        new Language("Italiano", "🇮🇹", "ITA", "it-IT", "it"),
        new Language("اردو (پاکستان)", "🇵🇰", "PAK", "ur-PK", "ur"),
        new Language("العربية", "🇸🇦", "SAU", "ar-SA", "ar"),
        new Language("हिन्दी", "🇮🇳", "IND", "hi-IN", "hi"),
        new Language("中文（普通话）", "🇨🇳", "CHN", "zh-CN", "zh-CN"),
        new Language("日本語", "🇯🇵", "JPN", "ja-JP", "ja"),
        new Language("한국어", "🇰🇷", "KOR", "ko-KR", "ko"),
        new Language("Русский", "🇷🇺", "RUS", "ru-RU", "ru"),
        new Language("Türkçe", "🇹🇷", "TUR", "tr-TR", "tr"),
        new Language("Bahasa Indonesia", "🇮🇩", "IDN", "id-ID", "id"),
        new Language("বাংলা", "🇧🇩", "BGD", "bn-BD", "bn"),
        new Language("ไทย", "🇹🇭", "THA", "th-TH", "th"),
        new Language("Tiếng Việt", "🇻🇳", "VNM", "vi-VN", "vi")
    };
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
            int initialWidth;
            boolean resizing;
            float distance(MotionEvent e) {
                if (e.getPointerCount() < 2) return 0f;
                return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
            }
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() >= 2) {
                    initialDistance = distance(e);
                    initialWidth = params.width > 0 ? params.width : getWidth();
                    resizing = initialDistance > 0;
                    return resizing;
                }
                if (resizing && action == MotionEvent.ACTION_MOVE && e.getPointerCount() >= 2) {
                    float currentDistance = distance(e);
                    if (initialDistance > 0 && currentDistance > 0) {
                        int minWidth = Math.min(dp(270), Math.max(dp(220), screenWidth - dp(24)));
                        int maxWidth = Math.max(minWidth, screenWidth - dp(16));
                        int nextWidth = Math.max(minWidth, Math.min(maxWidth,
                                Math.round(initialWidth * currentDistance / initialDistance)));
                        if (nextWidth != params.width) {
                            params.width = nextWidth;
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            params.x = Math.max(0, Math.min(params.x, screenWidth - nextWidth));
                            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        }
                    }
                    return true;
                }
                if (resizing && (action == MotionEvent.ACTION_POINTER_UP
                        || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                    resizing = false;
                    initialDistance = 0f;
                    return true;
                }
                if (resizing) return true;
                return super.dispatchTouchEvent(e);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable panelBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(7, 35, 91), Color.rgb(3, 15, 43)});
        panelBg.setCornerRadius(dp(22));
        panelBg.setStroke(dp(2), Color.rgb(0, 190, 255));
        panel.setBackground(panelBg);
        panel.setElevation(dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(7), dp(1), dp(2), dp(2));
        TextView title = makeText("WordFlow2", 17, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        direction = makeText("", 9, Color.rgb(117, 231, 255));
        updateLanguageLabels();
        direction.setGravity(Gravity.CENTER);
        direction.setTypeface(null, Typeface.BOLD);
        header.addView(direction, new LinearLayout.LayoutParams(0, dp(36), 1));
        Button minimizeButton = makeButton("−");
        Button closeButton = makeButton("×");
        styleRoundButton(minimizeButton, Color.rgb(18, 83, 153), 19);
        styleRoundButton(closeButton, Color.rgb(137, 35, 65), 22);
        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        controlLp.leftMargin = dp(4);
        header.addView(minimizeButton, controlLp);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        closeLp.leftMargin = dp(4);
        header.addView(closeButton, closeLp);
        panel.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
        contentLp.topMargin = dp(3);
        panel.addView(content, contentLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(4), dp(3), dp(4), dp(3));
        inputRow.setBackground(rounded(Color.rgb(9, 29, 66)));
        sourceFlag = makeText(sourceLanguage.flag, 21, Color.WHITE);
        sourceFlag.setGravity(Gravity.CENTER);
        sourceFlag.setBackground(circularIcon(Color.rgb(11, 49, 106)));
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        sourceLp.rightMargin = dp(6);
        inputRow.addView(sourceFlag, sourceLp);

        input = new EditText(this);
        input.setHint("Escribí una palabra o frase");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(165, 184, 211));
        input.setTextSize(14);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(2);
        input.setPadding(dp(8), dp(4), dp(5), dp(4));
        GradientDrawable editBg = rounded(Color.rgb(12, 38, 81));
        editBg.setStroke(dp(1), Color.rgb(31, 119, 207));
        input.setBackground(editBg);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button mic = makeButton("🎙");
        styleRoundButton(mic, Color.rgb(14, 93, 170), 18);
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        micLp.leftMargin = dp(6);
        inputRow.addView(mic, micLp);
        content.addView(inputRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(7);
        content.addView(actions, actionsLp);
        Button swap = makeButton("⇄");
        styleRoundButton(swap, Color.rgb(14, 58, 119), 23);
        GradientDrawable swapBg = circularIcon(Color.rgb(14, 58, 119));
        swap.setBackground(swapBg);
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        swapLp.rightMargin = dp(7);
        actions.addView(swap, swapLp);

        Button translate = makeButton("✦  TRADUCIR");
        translate.setTextColor(Color.rgb(1, 28, 61));
        translate.setTextSize(15);
        translate.setTypeface(null, Typeface.BOLD);
        GradientDrawable translateBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(37, 240, 238), Color.rgb(0, 167, 226)});
        translateBg.setCornerRadius(dp(24));
        translateBg.setStroke(dp(1), Color.rgb(131, 255, 255));
        translate.setBackground(translateBg);
        actions.addView(translate, new LinearLayout.LayoutParams(0, dp(48), 1));

        LinearLayout output = new LinearLayout(this);
        output.setGravity(Gravity.CENTER_VERTICAL);
        output.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable outputBg = rounded(Color.rgb(8, 28, 63));
        outputBg.setStroke(dp(1), Color.rgb(29, 100, 179));
        output.setBackground(outputBg);
        LinearLayout.LayoutParams outputLp = new LinearLayout.LayoutParams(-1, -2);
        outputLp.topMargin = dp(7);
        content.addView(output, outputLp);
        targetFlag = makeText(targetLanguage.flag, 21, Color.WHITE);
        targetFlag.setGravity(Gravity.CENTER);
        targetFlag.setBackground(circularIcon(Color.rgb(11, 49, 106)));
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        targetLp.rightMargin = dp(6);
        output.addView(targetFlag, targetLp);
        result = makeText("La traducción aparecerá aquí.", 14, Color.WHITE);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(dp(6), dp(5), dp(4), dp(5));
        result.setMinHeight(dp(42));
        output.addView(result, new LinearLayout.LayoutParams(0, -2, 1));

        Button speak = makeButton("🔊");
        styleRoundButton(speak, Color.rgb(15, 69, 132), 18);
        LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        speakLp.leftMargin = dp(4);
        output.addView(speak, speakLp);
        Button copy = makeButton("▣");
        styleRoundButton(copy, Color.rgb(15, 69, 132), 18);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        copyLp.leftMargin = dp(4);
        output.addView(copy, copyLp);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView hint = makeText("⠿ MOVER  ·  🤏 PELLIZCAR PARA AJUSTAR", 8, Color.rgb(152, 203, 245));
        hint.setPadding(dp(4), dp(3), dp(4), 0);
        footer.addView(hint);
        content.addView(footer);

        sourceFlag.setOnClickListener(v -> showLanguagePicker(true));
        targetFlag.setOnClickListener(v -> showLanguagePicker(false));
        direction.setOnClickListener(v -> showLanguagePicker(true));

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int initialWidth = Math.min(screenWidth - dp(16), Math.max(dp(270), (int)(screenWidth * 0.94f)));
        params = new WindowManager.LayoutParams(initialWidth, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(96);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

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
                        params.x = Math.max(0, Math.min(screenWidth - panel.getWidth(),
                                startX + (int)(e.getRawX() - downX)));
                        params.y = Math.max(0, Math.min(screenHeight - dp(80),
                                startY + (int)(e.getRawY() - downY)));
                        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
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
            content.setVisibility(minimized ? View.GONE : View.VISIBLE);
            minimizeButton.setText(minimized ? "+" : "−");
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
                    || value.equals("Traduciendo…") || value.equals("Escribí o dictá algo primero.")
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

    private void styleRoundButton(Button button, int color, int textSize) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(textSize);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable bg = circularIcon(color);
        button.setBackground(bg);
    }

    private void switchDirection() {
        Language previousSource = sourceLanguage;
        sourceLanguage = targetLanguage;
        targetLanguage = previousSource;
        updateLanguageLabels();
        setSpeechLanguage();
    }

    private void updateLanguageLabels() {
        if (direction != null) direction.setText(sourceLanguage.label() + "  ➜  " + targetLanguage.label());
        if (sourceFlag != null) sourceFlag.setText(sourceLanguage.flag);
        if (targetFlag != null) targetFlag.setText(targetLanguage.flag);
    }

    private void showLanguagePicker(boolean chooseSource) {
        Language selected = chooseSource ? sourceLanguage : targetLanguage;
        String[] names = new String[LANGUAGES.length];
        int selectedIndex = 0;
        for (int i = 0; i < LANGUAGES.length; i++) {
            names[i] = LANGUAGES[i].flag + "  " + LANGUAGES[i].name + " (" + LANGUAGES[i].displayCode + ")";
            if (LANGUAGES[i].localeTag.equals(selected.localeTag)) selectedIndex = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(chooseSource ? "Idioma de origen" : "Idioma de destino")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    Language choice = LANGUAGES[which];
                    Language other = chooseSource ? targetLanguage : sourceLanguage;
                    if (choice.localeTag.equals(other.localeTag)) {
                        Toast.makeText(this, "Elegí dos idiomas diferentes.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (chooseSource) sourceLanguage = choice;
                    else targetLanguage = choice;
                    updateLanguageLabels();
                    setSpeechLanguage();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
                @Override public void onReadyForSpeech(android.os.Bundle b) {
                    main.post(() -> Toast.makeText(OverlayService.this, "Te escucho…", Toast.LENGTH_SHORT).show());
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
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            reason = "Falta permiso de micrófono. Abrí WordFlow y autorizalo.";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            reason = "El reconocimiento de voz necesita conexión a internet.";
                            break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            reason = "No detecté palabras. Tocá el micrófono y hablá de nuevo.";
                            break;
                        default:
                            reason = "Falló el reconocimiento de voz. Revisá el permiso del micrófono.";
                    }
                    final String message = reason;
                    main.post(() -> Toast.makeText(OverlayService.this, message, Toast.LENGTH_LONG).show());
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
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguage.localeTag);
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
        final Language from = sourceLanguage;
        final Language to = targetLanguage;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String pair = from.apiCode + "|" + to.apiCode;
                // Normalize Argentine slang only for the Argentine-Spanish -> US-English pair.
                String translationInput = from.localeTag.equals("es-AR") && to.localeTag.equals("en-US")
                        ? ArgentineSpanishAdapter.prepareArgentineInputForTranslation(source)
                        : source;
                String encoded = URLEncoder.encode(translationInput, "UTF-8");
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
                final String finalText;
                if (to.localeTag.equals("es-AR") && from.localeTag.equals("en-US")) {
                    finalText = localizeArgentineSpanish(source, translated);
                } else if (to.localeTag.equals("en-US") && from.localeTag.equals("es-AR")) {
                    finalText = localizeNorthAmericanEnglish(source, translated);
                } else {
                    finalText = translated;
                }
                main.post(() -> result.setText(finalText));
            } catch (Exception e) {
                // If the network translator is unavailable, still serve exact, known
                // expressions locally in either direction instead of failing outright.
                final String fallback;
                if (to.localeTag.equals("es-AR") && from.localeTag.equals("en-US")) {
                    fallback = localizeArgentineSpanish(source, source);
                } else if (to.localeTag.equals("en-US") && from.localeTag.equals("es-AR")) {
                    fallback = localizeNorthAmericanEnglish(source, source);
                } else {
                    fallback = source;
                }
                main.post(() -> {
                    if (fallback != null && !fallback.equals(source)) {
                        result.setText(fallback);
                    } else {
                        result.setText("No se pudo traducir. Revisá internet e intentá otra vez.");
                    }
                });
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
            case "hello":
            case "hi":
            case "hey":
                return "Hola.";
            case "good morning":
                return "Buen día.";
            case "how are you":
            case "how are you doing":
                return "¿Cómo andás?";
            case "what's up":
            case "whats up":
                return "¿Qué onda?";
            case "what are you doing":
                return "¿Qué estás haciendo?";
            case "where are you":
                return "¿Dónde estás?";
            case "i'm on my way":
            case "im on my way":
                return "Ya estoy yendo.";
            case "i'm running late":
            case "im running late":
                return "Voy a llegar tarde.";
            case "hang on":
            case "hold on":
                return "Pará un segundo.";
            case "sounds good":
                return "Dale, buenísimo.";
            case "that's fine":
            case "thats fine":
                return "Está bien.";
            case "i'm kidding":
            case "im kidding":
                return "Estoy jodiendo.";
            case "i'm tired":
            case "im tired":
                return "Estoy cansado.";
            case "i'm so tired":
            case "im so tired":
                return "Estoy hecho bolsa.";
            case "i miss you":
                return "Te extraño.";
            case "take care":
                return "Cuidate.";
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

    // Context-aware common Argentine expressions to natural US English.
    // The generic translator handles sentences outside this explicit phrase list.
    private String localizeNorthAmericanEnglish(String source, String translated) {
        if (source == null || translated == null) return translated;
        String normalized = source.trim().toLowerCase(Locale.forLanguageTag("es-AR"))
                .replaceAll("[.!?¡¿]+$", "").trim();
        switch (normalized) {
            case "no tengo un mango":
            case "estoy sin un mango":
            case "estoy seco":
                return "I'm broke.";
            case "está todo piola":
            case "esta todo piola":
            case "todo piola":
                return "Everything's cool.";
            case "qué bajón":
            case "que bajon":
                return "That sucks.";
            case "me estás cargando":
            case "me estas cargando":
            case "me estás tomando el pelo":
            case "me estas tomando el pelo":
                return "Are you kidding me?";
            case "dale, nos vemos":
            case "dale nos vemos":
                return "Alright, see you.";
            case "se me fue la mano":
                return "I went too far.";
            case "me mandé una macana":
            case "me mande una macana":
                return "I messed up.";
            case "qué onda":
            case "que onda":
                return "What's up?";
            case "de una":
                return "Absolutely.";
            case "ni en pedo":
                return "No way.";
            case "estoy al horno":
                return "I'm screwed.";
            case "qué quilombo":
            case "que quilombo":
                return "What a mess.";
            case "estoy laburando":
                return "I'm working.";
            default:
                return translated;
        }
    }

    private void setSpeechLanguage() {
        if (ttsReady && tts != null) tts.setLanguage(Locale.forLanguageTag(targetLanguage.localeTag));
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
