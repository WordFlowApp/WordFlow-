package com.wordflow.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private WindowManager wm;
    private View panel;
    private EditText input;
    private TextView result;
    private TextView direction;
    private boolean northToArgentine = true;
    private WindowManager.LayoutParams windowParams;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate() {
        super.onCreate();
        createNotification();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        showPanel();
    }

    private void createNotification() {
        String channel = "wordflow_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(channel, "WordFlow flotante", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        b.setContentTitle("WordFlow está activo").setContentText("La ventana flotante está disponible.")
         .setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true);
        startForeground(7, b.build());
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(8, 29, 75));
        panelBg.setCornerRadius(dp(22));
        panelBg.setStroke(dp(1), Color.rgb(0, 220, 255));
        box.setBackground(panelBg);

        TextView heading = new TextView(this);
        heading.setText("WordFlow  🇺🇸 ⇄ 🇦🇷");
        heading.setTextColor(Color.CYAN);
        heading.setTextSize(18);
        heading.setPadding(0, 0, 0, dp(8));
        heading.setText("WordFlow  🇺🇸 ⇄ 🇦🇷  · mover");
        box.addView(heading);

        direction = new TextView(this);
        direction.setText("Norteamericano → Argentino   (tocar para invertir)");
        direction.setTextColor(Color.rgb(255, 220, 90));
        direction.setTextSize(13);
        direction.setPadding(0, 0, 0, dp(8));
        direction.setOnClickListener(v -> {
            northToArgentine = !northToArgentine;
            direction.setText(northToArgentine ? "Norteamericano → Argentino   (tocar para invertir)" : "Argentino → Norteamericano   (tocar para invertir)");
            input.setHint(northToArgentine ? "Escribí en norteamericano" : "Escribí en argentino");
        });
        box.addView(direction);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Escribí en norteamericano");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setBackgroundColor(Color.rgb(35, 55, 100));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout row = new LinearLayout(this);
        Button translate = new Button(this); translate.setText("Traducir");
        Button close = new Button(this); close.setText("Cerrar");
        row.addView(translate, new LinearLayout.LayoutParams(0, dp(48), 1));
        row.addView(close, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(row);

        result = new TextView(this);
        result.setText("La traducción aparecerá aquí.");
        result.setTextColor(Color.WHITE);
        result.setTextSize(16);
        result.setPadding(0, dp(8), 0, 0);
        box.addView(result);

        translate.setOnClickListener(v -> translateWord());
        close.setOnClickListener(v -> stopSelf());

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                               : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = dp(150);
        windowParams = p;
        heading.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = windowParams.x; startY = windowParams.y;
                        downX = event.getRawX(); downY = event.getRawY();
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        windowParams.x = startX + (int)(event.getRawX() - downX);
                        windowParams.y = Math.max(0, startY + (int)(event.getRawY() - downY));
                        try { wm.updateViewLayout(panel, windowParams); } catch (Exception ignored) {}
                        return true;
                    default: return true;
                }
            }
        });
        try { wm.addView(box, p); panel = box; }
        catch (Exception e) { stopSelf(); }
    }

    private void translateWord() {
        String word = input.getText().toString().trim();
        if (word.isEmpty()) { result.setText("Escribí una palabra primero."); return; }
        result.setText("Traduciendo…");
        worker.execute(() -> {
            String translated;
            try {
                String q = URLEncoder.encode(word, "UTF-8");
                String pair = northToArgentine ? "en|es" : "es|en";
                URL url = new URL("https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + pair);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close(); conn.disconnect();
                JSONObject json = new JSONObject(sb.toString());
                translated = json.getJSONObject("responseData").getString("translatedText");
            } catch (Exception e) {
                translated = "No se pudo traducir. Revisá internet e intentá otra vez.";
            }
            final String out = translated;
            new android.os.Handler(getMainLooper()).post(() -> { if (result != null) result.setText(out); });
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public void onDestroy() {
        if (panel != null && wm != null) try { wm.removeView(panel); } catch (Exception ignored) {}
        worker.shutdownNow();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
