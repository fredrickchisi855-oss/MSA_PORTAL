package com.msaportal.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String WEBSITE_URL =
            "http://127.0.0.1:8081/app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);

        webView.setBackgroundColor(Color.WHITE);

        WebSettings settings = webView.getSettings();

        // The APK uses the exact website JavaScript.
        settings.setJavaScriptEnabled(true);

        // Required for the portal's browser-side storage/state.
        settings.setDomStorageEnabled(true);

        // Allow the website to load normally.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Improve normal website compatibility.
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Local HTTP website.
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                return false;
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);

                /*
                 * The website uses window.open(..., "_blank")
                 * for result viewing.
                 *
                 * In the APK, keep that operation inside
                 * the same WebView instead of trying to open
                 * an external browser window.
                 */
                view.evaluateJavascript(
                        "window.open = function(url) {" +
                        "    if (url) {" +
                        "        window.location.href = url;" +
                        "    }" +
                        "};",
                        null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        setContentView(webView);

        // Open the SAME website used by the working browser version.
        webView.loadUrl(WEBSITE_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
