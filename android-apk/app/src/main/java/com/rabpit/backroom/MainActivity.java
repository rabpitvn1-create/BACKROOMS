package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
  private static final String GAME_URL = "https://backroom-rose.vercel.app";
  private WebView webView;

  @SuppressLint("SetJavaScriptEnabled")
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WebView.setWebContentsDebuggingEnabled(false);

    webView = new WebView(this);
    webView.setBackgroundColor(Color.rgb(8, 10, 12));
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setAllowFileAccessFromFileURLs(false);
    settings.setAllowUniversalAccessFromFileURLs(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setUserAgentString(settings.getUserAgentString() + " BackroomAndroid/1.1.25");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

    CookieManager cookies = CookieManager.getInstance();
    cookies.setAcceptCookie(true);
    cookies.setAcceptThirdPartyCookies(webView, false);

    webView.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return !isAllowedGameUrl(request.getUrl());
      }

      @SuppressWarnings("deprecation")
      @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return !isAllowedGameUrl(Uri.parse(url));
      }

      @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame()) showConnectionError();
      }
    });

    setContentView(webView);
    webView.loadUrl(GAME_URL);
  }

  private boolean isAllowedGameUrl(Uri uri) {
    return uri != null
      && "https".equalsIgnoreCase(uri.getScheme())
      && "backroom-rose.vercel.app".equalsIgnoreCase(uri.getHost());
  }

  private void showConnectionError() {
    String html = "<!doctype html><html lang='vi'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
      "<body style='margin:0;background:#080a0c;color:#eef1f3;font:16px system-ui;display:grid;place-items:center;min-height:100vh'>" +
      "<main style='max-width:420px;padding:28px;text-align:center'><h1>Không thể kết nối</h1>" +
      "<p style='color:#9aa4ad;line-height:1.6'>APK cần mạng để kết nối máy chủ BACKROOMS. API key được giữ an toàn trên server và không nằm trong ứng dụng.</p>" +
      "<button style='padding:12px 18px;background:#1a2025;color:white;border:1px solid #3a4249' onclick='location.href=\"" + GAME_URL + "\"'>Thử lại</button>" +
      "</main></body></html>";
    webView.loadDataWithBaseURL(GAME_URL, html, "text/html", "UTF-8", null);
  }

  @Override public void onBackPressed() {
    if (webView != null && webView.canGoBack()) webView.goBack();
    else super.onBackPressed();
  }

  @Override protected void onDestroy() {
    if (webView != null) webView.destroy();
    super.onDestroy();
  }
}
