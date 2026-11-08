package com.wordflow.app;

import android.service.autofill.AutofillService;
import android.service.autofill.FillRequest;
import android.service.autofill.FillCallback;
import android.service.autofill.FillResponse;
import android.service.autofill.Dataset;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.app.assist.AssistStructure;
import android.widget.RemoteViews;
import android.os.CancellationSignal;
import android.util.Log;
import java.util.Locale;

public class SubsAutofillService extends AutofillService {
    @Override public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        try {
            AssistStructure structure = request.getFillContexts().get(request.getFillContexts().size()-1).getStructure();
            Dataset.Builder dataset = new Dataset.Builder(presentation());
            int[] count = new int[]{0};
            for (int i=0; i<structure.getWindowNodeCount(); i++) {
                AssistStructure.WindowNode window = structure.getWindowNodeAt(i);
                scan(window.getRootViewNode(), dataset, count);
            }
            if (count[0] == 0) {
                callback.onSuccess(null);
            } else {
                FillResponse response = new FillResponse.Builder().addDataset(dataset.build()).build();
                callback.onSuccess(response);
            }
        } catch (Exception e) {
            Log.e("SUBS", "No se pudo analizar el formulario para autocompletar", e);
            callback.onSuccess(null);
        }
    }

    private RemoteViews presentation() {
        RemoteViews view = new RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
        view.setTextViewText(android.R.id.text1, "SUBS · Completar datos básicos");
        return view;
    }

    private void scan(AssistStructure.ViewNode node, Dataset.Builder dataset, int[] count) {
        if (node == null) return;
        if (node.getAutofillId() != null && !isSensitive(node)) {
            String field = identify(node);
            if (field != null) {
                String value = SubsProfileStore.get(this, field);
                if (value != null && !value.isEmpty()) {
                    dataset.setValue(node.getAutofillId(), AutofillValue.forText(value));
                    count[0]++;
                }
            }
        }
        for (int i=0; i<node.getChildCount(); i++) scan(node.getChildAt(i), dataset, count);
    }

    private boolean isSensitive(AssistStructure.ViewNode node) {
        if (node.isPassword()) return true;
        String all = ((String.valueOf(node.getHint()) + " " + String.valueOf(node.getIdEntry()) + " "
                + String.valueOf(node.getAutofillHints()) + " " + String.valueOf(node.getText()))
                .toLowerCase(Locale.ROOT));
        String[] blocked = {"password","passwd","contraseña","clave","pin","cvv","cvc","otp",
                "verification","verify code","security code","token","credit-card","credit_card",
                "card-number","card_number","iban","account-number","routing"};
        for (String term : blocked) if (all.contains(term)) return true;
        return false;
    }

    private String identify(AssistStructure.ViewNode node) {
        StringBuilder b = new StringBuilder();
        String[] hints = node.getAutofillHints();
        if (hints != null) for (String h : hints) b.append(h).append(' ');
        b.append(String.valueOf(node.getHint())).append(' ')
         .append(String.valueOf(node.getIdEntry())).append(' ')
         .append(String.valueOf(node.getText()));
        String s = b.toString().toLowerCase(Locale.ROOT);

        if (contains(s,"given-name","firstname","first_name","first name","nombre")) return "given_name";
        if (contains(s,"family-name","lastname","last_name","last name","surname","apellido")) return "family_name";
        if (contains(s,"email","e-mail","correo")) return "email";
        if (contains(s,"tel","phone","mobile","telefono","teléfono","celular")) return "phone";
        if (contains(s,"postal-code","postal_code","zip","postcode","codigo postal","código postal")) return "postal_code";
        if (contains(s,"address-level2","city","locality","ciudad","localidad")) return "city";
        if (contains(s,"address-level1","state","province","provincia")) return "province";
        if (contains(s,"country","país","pais")) return "country";
        if (contains(s,"street-address","address-line1","address","street","domicilio","calle")) return "street";
        if (contains(s,"birthdate","birth-date","bday","date of birth","fecha de nacimiento")) return "birth_date";
        if (contains(s,"name","full name","nombre completo")) return "full_name";
        return null;
    }

    private boolean contains(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    @Override public void onSaveRequest(android.service.autofill.SaveRequest request,
            android.service.autofill.SaveCallback callback) {
        // SUBS never silently learns data from another app. Users add/update fields explicitly in SUBS.
        callback.onSuccess();
    }
}
