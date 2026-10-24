package com.rabpit.backroom;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Private per-device provider configuration. No credential is compiled into a public APK,
 * returned to JavaScript, written into game saves, or included in diagnostics.
 */
final class ProviderCredentials {
  private static final String PREF_NAME = "backroom_provider_credentials";
  private static final String PREF_ENTRY = "encrypted_config_v1";
  private static final String KEY_ALIAS = "backroom_provider_credentials_aes_v1";
  private static final String[] FIELDS = {
    "GEHIHI_API_KEY", "GEHIHI_BASE_URL", "GEHIHI_MODEL",
    "GEMINI_API_KEY_1", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3",
    "GEMINI_API_KEY_4", "GEMINI_API_KEY_5"
  };
  private final SharedPreferences preferences;
  private JSONObject cached;

  ProviderCredentials(Context context) {
    preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  private static SecretKey encryptionKey() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    java.security.Key saved = store.getKey(KEY_ALIAS, null);
    if (saved instanceof SecretKey) return (SecretKey) saved;
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true).build());
    return generator.generateKey();
  }

  private JSONObject read() throws Exception {
    if (cached != null) return cached;
    String encoded = preferences.getString(PREF_ENTRY, null);
    if (encoded == null) {
      cached = new JSONObject();
      return cached;
    }
    try {
      byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
      if (data.length < 29) throw new IllegalStateException("encrypted_data_invalid");
      ByteBuffer packed = ByteBuffer.wrap(data);
      byte[] iv = new byte[12];
      packed.get(iv);
      byte[] ciphertext = new byte[packed.remaining()];
      packed.get(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
      cached = new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
      return cached;
    } catch (Exception failure) {
      throw new IllegalStateException("provider_credentials_unreadable", failure);
    }
  }

  synchronized String get(String field) throws Exception {
    return read().optString(field, "");
  }

  synchronized String[] geminiKeys() throws Exception {
    String[] keys = new String[5];
    JSONObject current = read();
    for (int i = 0; i < keys.length; i++) keys[i] = current.optString("GEMINI_API_KEY_" + (i + 1), "");
    return keys;
  }

  synchronized JSONObject status() throws Exception {
    JSONObject data = read();
    int keyCount = 0;
    for (int i = 1; i <= 5; i++)
      if (!data.optString("GEMINI_API_KEY_" + i, "").isEmpty()) keyCount++;
    return new JSONObject()
      .put("gehihi", !data.optString("GEHIHI_API_KEY", "").isEmpty()
        && !data.optString("GEHIHI_MODEL", "").isEmpty())
      .put("geminiKeys", keyCount);
  }

  synchronized JSONObject update(String raw) throws Exception {
    if (raw == null || raw.length() > 8192) throw new IllegalArgumentException("provider_config_invalid");
    JSONObject incoming = new JSONObject(raw);
    if (incoming.optBoolean("clear", false)) {
      if (!preferences.edit().remove(PREF_ENTRY).commit())
        throw new IllegalStateException("provider_config_save_failed");
      cached = new JSONObject();
      return status();
    }
    JSONObject next = new JSONObject(read().toString());
    for (String field : FIELDS) {
      if (!incoming.has(field)) continue;
      Object supplied = incoming.get(field);
      if (!(supplied instanceof String)) throw new IllegalArgumentException("provider_config_invalid");
      String value = ((String) supplied).trim();
      if (value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
        throw new IllegalArgumentException("provider_config_invalid");
      if (!value.isEmpty()) next.put(field, value);
    }
    String base = next.optString("GEHIHI_BASE_URL", "");
    if (!base.isEmpty()) {
      java.net.URL url = new java.net.URL(base);
      if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
          || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null)
        throw new IllegalArgumentException("provider_url_invalid");
    }
    byte[] cleartext = next.toString().getBytes(StandardCharsets.UTF_8);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
    byte[] iv = cipher.getIV();
    byte[] ciphertext = cipher.doFinal(cleartext);
    ByteBuffer packed = ByteBuffer.allocate(iv.length + ciphertext.length);
    packed.put(iv).put(ciphertext);
    String encoded = Base64.encodeToString(packed.array(), Base64.NO_WRAP);
    if (!preferences.edit().putString(PREF_ENTRY, encoded).commit())
      throw new IllegalStateException("provider_config_save_failed");
    cached = next;
    return status();
  }
}
