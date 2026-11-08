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
    private static final String OVERLAY_PREFS = "subs_overlay_position";
    @Override protected void onCreate(Bundle state) { super.onCreate(state); showHome(); }
    @Override protected void onResume() { super.onResume(); }
    private void startOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent service = new Intent(this, SubsOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        Toast.makeText(this, "SUBS flotante activado.", Toast.LENGTH_SHORT).show();
    }
    private void stopOverlay() {
        stopService(new Intent(this, SubsOverlayService.class));
        Toast.makeText(this, "SUBS flotante ocultado. Tus datos siguen guardados.", Toast.LENGTH_SHORT).show();
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD)); return t;
    }
    private Button button(String label) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(15);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(RED); bg.setCornerRadius(dp(14)); b.setBackground(bg); return b;
    }
    private void showHome() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(26),dp(22),dp(36)); root.setBackgroundColor(Color.WHITE); scroll.addView(root);
        TextView logo=text("SUBS",38,Color.WHITE,true); logo.setGravity(Gravity.CENTER);
        GradientDrawable lg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFFF7777,0xFFED001B,0xFFB00012});
        lg.setCornerRadius(dp(24)); logo.setBackground(lg); root.addView(logo,new LinearLayout.LayoutParams(-1,dp(94)));
        TextView heading=text("Tus datos, una sola vez.",23,Color.BLACK,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(20);root.addView(heading,hp);
        TextView sub=text("Guardá tus datos básicos. SUBS los ofrece cuando una app o página compatible solicita esos campos.",15,Color.DKGRAY,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(7);root.addView(sub,sp);
        addField(root,"given_name","Nombre"); addField(root,"family_name","Apellido");
        addField(root,"full_name","Nombre completo (opcional)"); addField(root,"email","Correo electrónico");
        addField(root,"phone","Teléfono"); addField(root,"street","Calle y número");
        addField(root,"city","Ciudad"); addField(root,"province","Provincia");
        addField(root,"postal_code","Código postal"); addField(root,"country","País");
        addField(root,"birth_date","Fecha de nacimiento");
        Button save=button("GUARDAR MIS DATOS"); addButton(root,save,()->saveProfile());
        Button autofill=button("Activar SUBS como autocompletado"); addButton(root,autofill,()->{
            if(Build.VERSION.SDK_INT>=26)try{startActivity(new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
            else Toast.makeText(this,"El autocompletado requiere Android 8.0 o superior.",Toast.LENGTH_LONG).show();
        });
        Button direct=button("Activar modo directo (opcional)"); addButton(root,direct,()->{
            Toast.makeText(this,"En la pantalla siguiente podés habilitar SUBS si Android lo permite. Es opcional.",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        Button overlay=button("MOSTRAR ICONO FLOTANTE SUBS"); addButton(root,overlay,()->startOverlay());
        Button hide=button("OCULTAR ICONO FLOTANTE SUBS"); addButton(root,hide,()->stopOverlay());
        TextView safety=text("Privacidad: los datos del perfil se cifran localmente con Android Keystore. El modo directo opcional solo revisa campos editables visibles cuando tocás SUBS; no registra pulsaciones ni toma capturas. Nunca completa contraseñas, PIN, CVV, OTP ni credenciales bancarias. Nada se envía a servidores.",13,Color.DKGRAY,false);
        LinearLayout.LayoutParams safe=new LinearLayout.LayoutParams(-1,-2);safe.topMargin=dp(18);root.addView(safety,safe);
        setContentView(scroll); loadProfile();
    }
    private void addButton(LinearLayout root,Button b,Runnable action) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(10);root.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private void addField(LinearLayout root,String key,String label) {
        TextView l=text(label,14,Color.DKGRAY,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(13);root.addView(l,lp);
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setPadding(dp(12),dp(7),dp(12),dp(7));
        e.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
        if("email".equals(key))e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if("phone".equals(key))e.setInputType(InputType.TYPE_CLASS_PHONE);
        if("birth_date".equals(key))e.setHint("DD/MM/AAAA");
        inputs.put(key,e);root.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void saveProfile() {
        String first=inputs.get("given_name").getText().toString().trim();
        String last=inputs.get("family_name").getText().toString().trim();
        if(first.isEmpty() || last.isEmpty()) {
            Toast.makeText(this,"Completá al menos tu nombre y apellido antes de guardar.",Toast.LENGTH_LONG).show();
            return;
        }
        try {
            for(Map.Entry<String,EditText> e:inputs.entrySet()) SubsProfileStore.save(this,e.getKey(),e.getValue().getText().toString());
            new android.app.AlertDialog.Builder(this)
                    .setTitle("¡Datos guardados!")
                    .setMessage("¡Tus datos están guardados y SUBS está listo para ayudarte!")
                    .setPositiveButton("Aceptar",null).show();
        } catch(Exception e) {
            Toast.makeText(this,"No se pudieron cifrar los datos. Intentá nuevamente.",Toast.LENGTH_LONG).show();
        }
    }
    private void loadProfile() {
        for(Map.Entry<String,EditText> e:inputs.entrySet()){String value=SubsProfileStore.get(this,e.getKey());if(value!=null)e.getValue().setText(value);}
    }
}
