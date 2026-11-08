package com.wordflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.text.InputType;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private static final int RED = Color.rgb(235, 0, 20);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) startOverlay();
    }

    private void startOverlay() {
        Intent service = new Intent(this, SubsOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RED); bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        return b;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(36));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView logo = text("SUBS", 38, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5555, 0xFFED001B, 0xFFB00012});
        logoBg.setCornerRadius(dp(24)); logo.setBackground(logoBg);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(94)));

        TextView heading = text("Tus datos, una sola vez.", 23, Color.BLACK, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(20);
        root.addView(heading,hp);
        TextView subtitle = text("Guardá tus datos básicos. SUBS los ofrecerá cuando una aplicación o página compatible solicite esos campos.",15,Color.DKGRAY,false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(7);
        root.addView(subtitle,sp);

        addField(root,"given_name","Nombre");
        addField(root,"family_name","Apellido");
        addField(root,"full_name","Nombre completo (opcional)");
        addField(root,"email","Correo electrónico");
        addField(root,"phone","Teléfono");
        addField(root,"street","Calle y número");
        addField(root,"city","Ciudad");
        addField(root,"province","Provincia");
        addField(root,"postal_code","Código postal");
        addField(root,"country","País");
        addField(root,"birth_date","Fecha de nacimiento");

        Button save = button("Guardar mis datos de forma cifrada");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,dp(54)); bp.topMargin=dp(18);
        root.addView(save,bp);
        save.setOnClickListener(v -> saveProfile());

        Button autofill = button("Activar SUBS como autocompletado");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1,dp(54)); ap.topMargin=dp(10);
        root.addView(autofill,ap);
        autofill.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            } else Toast.makeText(this,"El autocompletado de SUBS requiere Android 8.0 o superior.",Toast.LENGTH_LONG).show();
        });

        Button overlay = button("Activar icono flotante SUBS");
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1,dp(54)); op.topMargin=dp(10);
        root.addView(overlay,op);
        overlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else startOverlay();
        });

        TextView safety = text("Privacidad: los datos del perfil se cifran en el dispositivo con una clave de Android Keystore. SUBS no guarda ni completa contraseñas, PIN, CVV, códigos OTP ni credenciales bancarias. No aprende datos de otras apps en secreto.",13,Color.DKGRAY,false);
        LinearLayout.LayoutParams safe = new LinearLayout.LayoutParams(-1,-2); safe.topMargin=dp(18);
        root.addView(safety,safe);
        setContentView(scroll);
        loadProfile();
    }

    private void addField(LinearLayout root, String key, String label) {
        TextView l = text(label,14,Color.DKGRAY,true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(13);
        root.addView(l,lp);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12),dp(7),dp(12),dp(7));
        input.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
        if ("email".equals(key)) input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if ("phone".equals(key)) input.setInputType(InputType.TYPE_CLASS_PHONE);
        if ("birth_date".equals(key)) input.setHint("DD/MM/AAAA");
        inputs.put(key,input);
        root.addView(input,new LinearLayout.LayoutParams(-1,dp(48)));
    }

    private void saveProfile() {
        try {
            for (Map.Entry<String,EditText> e : inputs.entrySet()) SubsProfileStore.save(this,e.getKey(),e.getValue().getText().toString());
            Toast.makeText(this,"Datos guardados y cifrados en este dispositivo.",Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,"No se pudieron cifrar los datos. Intentá nuevamente.",Toast.LENGTH_LONG).show();
        }
    }

    private void loadProfile() {
        for (Map.Entry<String,EditText> e : inputs.entrySet()) {
            String value=SubsProfileStore.get(this,e.getKey());
            if (value!=null) e.getValue().setText(value);
        }
    }
}
