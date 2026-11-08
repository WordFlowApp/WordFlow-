package com.wordflow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class SubsOverlayService extends Service {
    private static final String CHANNEL = "subs_overlay";
    private static final String PREFS = "subs_overlay_position";
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private TextView bubble;
    private float downX, downY;
    private int startX, startY;
    private boolean moved;

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Falta el permiso para mostrar SUBS sobre otras aplicaciones.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        startForeground(41, buildNotification());
        attachBubble();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "SUBS icono flotante", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("SUBS está activo")
                .setContentText("Tocá SUBS para completar campos permitidos. Mantené presionado para ocultarlo.")
                .setOngoing(true).build();
    }

    private GradientDrawable background() {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF7777, 0xFFED001B, 0xFFB00012});
        shape.setCornerRadius(dp(22));
        shape.setStroke(dp(1), 0xFFFFD0C8);
        return shape;
    }

    private void attachBubble() {
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setText("SUBS");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(18);
        bubble.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(7), 0, dp(7), 0);
        bubble.setElevation(dp(9));
        bubble.setBackground(background());

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(104), dp(62), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        params.x = prefs.getInt("x", dp(8));
        params.y = prefs.getInt("y", dp(150));
        bubble.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = params.x; startY = params.y; moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(event.getRawX() - downX);
                    int dy = (int)(event.getRawY() - downY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    if (moved) {
                        params.x = Math.max(0, startX + dx);
                        params.y = Math.max(0, startY + dy);
                        try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putInt("x", params.x).putInt("y", params.y).apply();
                    } else if (event.getEventTime() - event.getDownTime() > 600) {
                        Toast.makeText(this, "SUBS ocultado. Podés volver a mostrarlo desde la aplicación.", Toast.LENGTH_LONG).show();
                        stopSelf();
                    } else {
                        Intent fill = new Intent(SubsDirectFillService.ACTION_FILL);
                        fill.setPackage(getPackageName());
                        sendBroadcast(fill);
                        Toast.makeText(this, "SUBS: buscando campos básicos permitidos…", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return false;
        });
        try {
            windowManager.addView(bubble, params);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo mostrar SUBS. Revisá el permiso de superposición.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
