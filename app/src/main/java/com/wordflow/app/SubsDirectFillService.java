package com.wordflow.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.Locale;

public class SubsDirectFillService extends AccessibilityService {
    public static final String ACTION_FILL = "com.subs.app.ACTION_FILL_CURRENT_FORM";
    private BroadcastReceiver receiver;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ACTION_FILL.equals(intent.getAction())) fillVisibleForm();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_FILL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    private void fillVisibleForm() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Toast.makeText(this, "No hay un formulario compatible visible.", Toast.LENGTH_SHORT).show();
            return;
        }
        int filled = fillTree(root);
        Toast.makeText(this, filled > 0 ? "SUBS completó " + filled + " campo(s) permitido(s)." :
                "No encontré campos básicos compatibles. Probá tocar el campo y usar SUBS desde Autocompletar.",
                Toast.LENGTH_LONG).show();
    }

    private int fillTree(AccessibilityNodeInfo node) {
        if (node == null) return 0;
        int count = 0;
        if (node.isEditable() && !isSensitive(node)) {
            String field = identify(node);
            if (field != null) {
                String value = SubsProfileStore.get(this, field);
                CharSequence current = node.getText();
                if (value != null && !value.trim().isEmpty() &&
                        (current == null || current.length() == 0)) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) count++;
                }
            }
        }
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                count += fillTree(child);
                child.recycle();
            }
        }
        return count;
    }

    private boolean isSensitive(AccessibilityNodeInfo n) {
        if (n.isPassword()) return true;
        int type=n.getInputType();
        int variation=type & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION);
        if (variation == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) ||
            variation == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            variation == (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD)) return true;
        String s=metadata(n);
        String[] blocked={"password","passwd","contraseña","clave","pin","cvv","cvc","otp",
            "verification","verify code","security code","token","credit-card","credit_card",
            "card-number","card_number","iban","account-number","routing","username","user_name","login"};
        for(String term:blocked) if(s.contains(term)) return true;
        return false;
    }

    private String metadata(AccessibilityNodeInfo n) {
        StringBuilder b=new StringBuilder();
        if(n.getHintText()!=null)b.append(n.getHintText()).append(' ');
        if(n.getContentDescription()!=null)b.append(n.getContentDescription()).append(' ');
        if(n.getViewIdResourceName()!=null)b.append(n.getViewIdResourceName()).append(' ');
        if(n.getText()!=null)b.append(n.getText());
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private String identify(AccessibilityNodeInfo n) {
        String s=metadata(n);
        if(has(s,"given-name","firstname","first_name","first name","nombre"))return "given_name";
        if(has(s,"family-name","lastname","last_name","last name","surname","apellido"))return "family_name";
        if(has(s,"email","e-mail","correo"))return "email";
        if(has(s,"phone","mobile","telefono","teléfono","celular","tel_number"))return "phone";
        if(has(s,"postal-code","postal_code","zip","postcode","codigo postal","código postal"))return "postal_code";
        if(has(s,"address-level2","city","locality","ciudad","localidad"))return "city";
        if(has(s,"address-level1","state","province","provincia"))return "province";
        if(has(s,"country","país","pais"))return "country";
        if(has(s,"street-address","address-line1","street","domicilio","calle","address"))return "street";
        if(has(s,"birthdate","birth-date","bday","date of birth","fecha de nacimiento"))return "birth_date";
        if(has(s,"full name","nombre completo"))return "full_name";
        if(s.trim().equals("name") || s.contains("name_field"))return "full_name";
        return null;
    }

    private boolean has(String s,String... terms) {
        for(String t:terms)if(s.contains(t))return true;
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if(receiver!=null) {
            try { unregisterReceiver(receiver); } catch(Exception ignored) { }
            receiver=null;
        }
        super.onDestroy();
    }
}
