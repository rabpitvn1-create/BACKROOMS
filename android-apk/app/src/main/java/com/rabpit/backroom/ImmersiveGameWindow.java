package com.rabpit.backroom;

import android.app.Activity;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebView;
import java.util.Locale;

/** Full-screen game surface; controls stay clear of the display cutout. */
final class ImmersiveGameWindow {
  private ImmersiveGameWindow() {}

  static void install(Activity activity, WebView webView) {
    Window window = activity.getWindow();
    if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    if (Build.VERSION.SDK_INT >= 28) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      window.setAttributes(attributes);
    }
    webView.setOnApplyWindowInsetsListener((view, insets) -> {
      applySafeArea(webView, insets);
      return insets;
    });
    webView.post(() -> { hide(activity); webView.requestApplyInsets(); });
  }

  @SuppressWarnings("deprecation")
  static void hide(Activity activity) {
    Window window = activity.getWindow();
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) {
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsets.Type.systemBars());
      }
    } else {
      window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  static void applySafeArea(WebView webView, WindowInsets insets) {
    if (insets == null) return;
    DisplayCutout cutout = Build.VERSION.SDK_INT >= 28 ? insets.getDisplayCutout() : null;
    float density = webView.getResources().getDisplayMetrics().density;
    String script = String.format(Locale.ROOT,
      "(function(){if(!document.documentElement)return;var s=document.documentElement.style;s.setProperty('--android-safe-top','%.2fpx');"
      + "s.setProperty('--android-safe-right','%.2fpx');s.setProperty('--android-safe-bottom','%.2fpx');"
      + "s.setProperty('--android-safe-left','%.2fpx');})();",
      cutout == null ? 0f : cutout.getSafeInsetTop() / density,
      cutout == null ? 0f : cutout.getSafeInsetRight() / density,
      cutout == null ? 0f : cutout.getSafeInsetBottom() / density,
      cutout == null ? 0f : cutout.getSafeInsetLeft() / density);
    webView.evaluateJavascript(script, null);
  }
}
