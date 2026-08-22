package com.rabpit.backroom;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class DriveOnlineSaveManager {
  static final int SIGN_IN_REQUEST = 7301;
  static final String SAVE_FOLDER_ID = "1R9UMFmDmLaGdu4pB2_ha07mMfWywgEaF";
  static final String DEFAULT_SAVE_NAME = "backroom-online-save.json";
  private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";

  interface Callback {
    void ok(String json);
    void error(String message);
  }

  private final Activity activity;
  private final GoogleSignInClient signInClient;
  private volatile GoogleSignInAccount account;

  DriveOnlineSaveManager(Activity activity) {
    this.activity = activity;
    GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestEmail()
      .requestScopes(new Scope(DRIVE_SCOPE))
      .build();
    signInClient = GoogleSignIn.getClient(activity, options);
    account = GoogleSignIn.getLastSignedInAccount(activity);
  }

  boolean isSignedIn() {
    GoogleSignInAccount current = account;
    return current != null && GoogleSignIn.hasPermissions(current, new Scope(DRIVE_SCOPE));
  }

  void startSignIn() {
    activity.startActivityForResult(signInClient.getSignInIntent(), SIGN_IN_REQUEST);
  }

  void handleSignInResult(Intent data, Callback callback) {
    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
    try {
      GoogleSignInAccount signed = task.getResult(Exception.class);
      if (signed == null || !GoogleSignIn.hasPermissions(signed, new Scope(DRIVE_SCOPE))) {
        callback.error("Tài khoản Google chưa cấp quyền Google Drive.");
        return;
      }
      account = signed;
      callback.ok(new JSONObject().put("signedIn", true).put("email", signed.getEmail() == null ? "" : signed.getEmail()).toString());
    } catch (Exception e) {
      callback.error("Đăng nhập Google Drive thất bại: " + safeMessage(e));
    }
  }

  String saveDefault(String stateJson) throws Exception {
    requireSignedIn();
    JSONObject state = new JSONObject(stateJson);
    JSONObject envelope = new JSONObject()
      .put("format", "backroom-save-v1")
      .put("name", DEFAULT_SAVE_NAME)
      .put("savedAt", System.currentTimeMillis())
      .put("turn", state.optInt("turn", 1))
      .put("state", state);
    String token = accessToken();
    String fileId = findFileId(token, DEFAULT_SAVE_NAME);
    if (fileId == null || fileId.isEmpty()) fileId = createFile(token, DEFAULT_SAVE_NAME, envelope.toString());
    else updateFile(token, fileId, envelope.toString());
    return new JSONObject()
      .put("ok", true)
      .put("fileId", fileId)
      .put("name", DEFAULT_SAVE_NAME)
      .put("turn", state.optInt("turn", 1))
      .put("folderId", SAVE_FOLDER_ID)
      .toString();
  }

  String loadDefault() throws Exception {
    requireSignedIn();
    String token = accessToken();
    String fileId = findFileId(token, DEFAULT_SAVE_NAME);
    if (fileId == null || fileId.isEmpty()) throw new Exception("Chưa có save online trong thư mục Drive.");
    String body = request("GET", "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media", token, null, null);
    JSONObject envelope = new JSONObject(body);
    JSONObject state = envelope.optJSONObject("state");
    if (state == null) throw new Exception("Save Drive không chứa state hợp lệ.");
    return new JSONObject().put("ok", true).put("fileId", fileId).put("name", DEFAULT_SAVE_NAME).put("state", state).toString();
  }

  private void requireSignedIn() throws Exception {
    if (!isSignedIn()) throw new Exception("Chưa kết nối Google Drive. Hãy đăng nhập Google trước.");
  }

  private String accessToken() throws Exception {
    GoogleSignInAccount current = account;
    if (current == null) throw new Exception("Chưa đăng nhập Google.");
    Account raw = current.getAccount();
    if (raw == null) throw new Exception("Không lấy được Google Account.");
    try {
      return GoogleAuthUtil.getToken(activity, raw, "oauth2:" + DRIVE_SCOPE);
    } catch (GoogleAuthException e) {
      throw new Exception("Google Drive từ chối cấp access token: " + safeMessage(e));
    }
  }

  private String findFileId(String token, String name) throws Exception {
    String q = "'" + SAVE_FOLDER_ID + "' in parents and name='" + name.replace("'", "\\'") + "' and trashed=false";
    String url = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") + "&spaces=drive&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=1";
    JSONObject result = new JSONObject(request("GET", url, token, null, null));
    JSONArray files = result.optJSONArray("files");
    if (files == null || files.length() == 0) return null;
    JSONObject first = files.optJSONObject(0);
    return first == null ? null : first.optString("id", null);
  }

  private String createFile(String token, String name, String json) throws Exception {
    String boundary = "BackroomSaveBoundary" + System.currentTimeMillis();
    JSONObject metadata = new JSONObject().put("name", name).put("parents", new JSONArray().put(SAVE_FOLDER_ID));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n").getBytes(StandardCharsets.UTF_8));
    bytes.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + json + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    String body = requestBytes("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name", token, "multipart/related; boundary=" + boundary, bytes.toByteArray());
    return new JSONObject(body).getString("id");
  }

  private void updateFile(String token, String fileId, String json) throws Exception {
    request("PATCH", "https://www.googleapis.com/upload/drive/v3/files/" + fileId + "?uploadType=media", token, "application/json; charset=UTF-8", json);
  }

  private String request(String method, String url, String token, String contentType, String body) throws Exception {
    byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
    return requestBytes(method, url, token, contentType, bytes);
  }

  private String requestBytes(String method, String url, String token, String contentType, byte[] body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
    connection.setRequestMethod(method);
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(45000);
    connection.setRequestProperty("Authorization", "Bearer " + token);
    connection.setRequestProperty("Accept", "application/json");
    if (body != null) {
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", contentType == null ? "application/json" : contentType);
      try (OutputStream out = connection.getOutputStream()) { out.write(body); }
    }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder text = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) text.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = text.length() > 500 ? text.substring(0, 500) : text.toString();
      throw new Exception("Google Drive HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return text.toString();
  }

  private static String safeMessage(Throwable error) {
    String message = error == null ? null : error.getMessage();
    return message == null || message.trim().isEmpty() ? "unknown error" : message.trim();
  }
}
