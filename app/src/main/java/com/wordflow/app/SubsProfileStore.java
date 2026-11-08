package com.wordflow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SubsProfileStore {
    private static final String PREFS = "subs_profile_secure";
    private static final String KEY_ALIAS = "subs_profile_aes_key";
    private static final String[] FIELDS = {
        "given_name","family_name","full_name","email","phone","street","city","province","postal_code","country","birth_date"
    };
    private SubsProfileStore() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        android.security.keystore.KeyGenParameterSpec spec =
            new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build();
        generator.init(spec);
        return generator.generateKey();
    }

    static void save(Context context, String field, String value) throws Exception {
        if (!isAllowed(field)) return;
        if (value == null || value.trim().isEmpty()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(field).apply();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv,0,combined,0,iv.length);
        System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(field, Base64.encodeToString(combined, Base64.NO_WRAP)).apply();
    }

    static String get(Context context, String field) {
        if (!isAllowed(field)) return null;
        try {
            String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(field, null);
            if (stored == null) return null;
            byte[] combined = Base64.decode(stored, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(combined,0,iv,0,12);
            byte[] encrypted = new byte[combined.length-12];
            System.arraycopy(combined,12,encrypted,0,encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    static boolean isAllowed(String field) {
        for (String f : FIELDS) if (f.equals(field)) return true;
        return false;
    }
    static String[] fields() { return FIELDS.clone(); }
}
