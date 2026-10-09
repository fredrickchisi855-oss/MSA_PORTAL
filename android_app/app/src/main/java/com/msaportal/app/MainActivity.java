package com.msaportal.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.graphics.Color;
import android.view.Window;
import android.webkit.ValueCallback;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private DownloadManager downloadManager;
    private BroadcastReceiver downloadReceiver;
    private final Set<Long> openAfterDownload = new HashSet<>();

    private static final int FILE_CHOOSER_REQUEST = 7412;

    private static final String WEBSITE_URL =
            "http://127.0.0.1:8081/app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        downloadManager =
                (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        registerPdfDownloadReceiver();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        // Connect the website's existing PDF buttons to Android.
        webView.addJavascriptInterface(
                new AndroidMSABridge(), "AndroidMSA"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request
            ) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, String url
            ) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                view.evaluateJavascript(
                        "window.open = function(url) {" +
                        " if (url) { window.location.href = url; }" +
                        "};",
                        null
                );
            }
        });

        // Handle PDF links that WebView tries to download directly.
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimeType,
                    long contentLength
            ) {
                enqueuePdf(url, "MSA_PORTAL_Result.pdf", false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }

                pendingFileCallback = callback;

                Intent intent =
                        new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/pdf");
                intent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                try {
                    startActivityForResult(
                            intent, FILE_CHOOSER_REQUEST
                    );
                } catch (Exception e) {
                    pendingFileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to open the PDF picker.",
                            Toast.LENGTH_LONG
                    ).show();
                    return false;
                }

                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl(WEBSITE_URL);
    }

    public class AndroidMSABridge {

        @JavascriptInterface
        public void viewResult(String path) {
            String url = resolveUrl(path);
            if (url == null) {
                return;
            }

            Toast.makeText(
                    MainActivity.this,
                    "Opening result PDF...",
                    Toast.LENGTH_SHORT
            ).show();

            final String cookies = CookieManager.getInstance().getCookie(url);
            final String userAgent = webView.getSettings().getUserAgentString();

            new Thread(() -> {
                java.net.HttpURLConnection connection = null;

                try {
                    java.net.URL target = new java.net.URL(url);
                    connection = (java.net.HttpURLConnection)
                            target.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);

                    if (cookies != null && !cookies.isEmpty()) {
                        connection.setRequestProperty("Cookie", cookies);
                    }

                    connection.setRequestProperty("User-Agent", userAgent);

                    int status = connection.getResponseCode();
                    if (status != 200) {
                        throw new java.io.IOException(
                                "Server returned HTTP " + status
                        );
                    }

                    java.io.File pdfFolder = new java.io.File(
                            MainActivity.this.getCacheDir(), "result_pdfs"
                    );
                    if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                        throw new java.io.IOException(
                                "Could not create temporary PDF folder"
                        );
                    }

                    java.io.File pdfFile = new java.io.File(
                            pdfFolder,
                            "MSA_Result_" + System.currentTimeMillis() + ".pdf"
                    );

                    try (
                        java.io.InputStream input = connection.getInputStream();
                        java.io.FileOutputStream output =
                                new java.io.FileOutputStream(pdfFile)
                    ) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }

                    if (!pdfFile.isFile() || pdfFile.length() == 0) {
                        throw new java.io.IOException("The PDF file is empty");
                    }

                    runOnUiThread(() -> {
            try {
                Intent intent = new Intent(
                        MainActivity.this, PdfViewerActivity.class
                );
                intent.putExtra(
                        PdfViewerActivity.EXTRA_PDF_PATH,
                        pdfFile.getAbsolutePath()
                );
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(
                        MainActivity.this,
                        "Could not display PDF: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });

                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Could not open PDF: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void openInChrome(String path) {
            String url = resolveUrl(path);
            if (url == null) {
                return;
            }

            runOnUiThread(() -> {
                try {
                    Intent chrome = new Intent(
                            Intent.ACTION_VIEW, Uri.parse(url)
                    );
                    chrome.setPackage("com.android.chrome");
                    startActivity(chrome);
                } catch (Exception chromeError) {
                    try {
                        Intent browser = new Intent(
                                Intent.ACTION_VIEW, Uri.parse(url)
                        );
                        startActivity(browser);
                    } catch (Exception e) {
                        Toast.makeText(
                                MainActivity.this,
                                "Could not open PDF in a browser: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void downloadResult(String path, String fileName) {
            runOnUiThread(() -> {
                String url = resolveUrl(path);
                if (url != null) {
                    enqueuePdf(url, fileName, false);
                }
            });
        }
    }

    private String resolveUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, "Invalid PDF link.", Toast.LENGTH_LONG)
                    .show();
            return null;
        }

        String base = WEBSITE_URL.endsWith("/") ? WEBSITE_URL : WEBSITE_URL + "/";
        Uri candidate = Uri.parse(path);
        Uri result;

        if (path.startsWith("http://") || path.startsWith("https://")) {
            result = candidate;
        } else {
            result = Uri.parse(path.startsWith("/")
                    ? "http://127.0.0.1:8081" + path
                    : base + path);
        }
        if (result == null ||
                !WEBSITE_URL.startsWith("http://127.0.0.1:8081/") ||
                !"127.0.0.1".equals(result.getHost()) ||
                result.getPort() != 8081) {
            Toast.makeText(this, "Invalid PDF address.", Toast.LENGTH_LONG)
                    .show();
            return null;
        }

        return result.toString();
    }

    private void enqueuePdf(
            String url, String fileName, boolean openWhenReady
    ) {
        try {
            Uri uri = Uri.parse(url);

            if (!"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() != 8081) {
                Toast.makeText(
                        this, "Invalid PDF address.", Toast.LENGTH_LONG
                ).show();
                return;
            }

            String safeName = fileName == null
                    ? "MSA_PORTAL_Result.pdf"
                    : fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

            if (!safeName.toLowerCase().endsWith(".pdf")) {
                safeName += ".pdf";
            }

            DownloadManager.Request request =
                    new DownloadManager.Request(uri);

            request.setTitle(safeName);
            request.setDescription("Mwashita Academy student result");
            request.setMimeType("application/pdf");
            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            if (openWhenReady) {
                // VIEW: keep the PDF in the app's private external files
                // area, then open it in an installed PDF viewer.
                request.setDestinationInExternalFilesDir(
                        this, Environment.DIRECTORY_DOCUMENTS, safeName
                );
            } else {
                // DOWNLOAD: save the PDF in the phone's Downloads folder.
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, safeName
                );
            }
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            // Reuse the website's session when the server requires login.
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }

            if (webView != null) {
                request.addRequestHeader(
                        "User-Agent",
                        webView.getSettings().getUserAgentString()
                );
            }

            long downloadId = downloadManager.enqueue(request);

            if (openWhenReady) {
                openAfterDownload.add(downloadId);
            }

            Toast.makeText(
                    this,
                    openWhenReady
                            ? "Downloading result to open it…"
                            : "PDF download started. Check Downloads.",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this, "Could not download the PDF.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void registerPdfDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE
                        .equals(intent.getAction())) {
                    return;
                }

                long id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                );

                if (!openAfterDownload.remove(id)) {
                    return;
                }

                DownloadManager.Query query =
                        new DownloadManager.Query().setFilterById(id);

                try (android.database.Cursor cursor =
                             downloadManager.query(query)) {

                    if (cursor == null || !cursor.moveToFirst()) {
                        showPdfError();
                        return;
                    }

                    int status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_STATUS
                            )
                    );

                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        showPdfError();
                        return;
                    }

                    Uri fileUri =
                            downloadManager.getUriForDownloadedFile(id);

                    if (fileUri == null) {
                        showPdfError();
                        return;
                    }

                    Intent openIntent = new Intent(
                            Intent.ACTION_VIEW, fileUri
                    );
                    openIntent.setDataAndType(
                            fileUri, "application/pdf"
                    );
                    openIntent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

                    try {
                        startActivity(openIntent);
                    } catch (Exception e) {
                        Toast.makeText(
                                MainActivity.this,
                                "PDF saved in Downloads. Install a PDF viewer to open it.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                } catch (Exception e) {
                    showPdfError();
                }
            }
        };

        IntentFilter filter = new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE
        );

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(downloadReceiver, filter);
        }
    }

    private void showPdfError() {
        Toast.makeText(
                this,
                "PDF download failed. Check the portal connection and try again.",
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK
                    && data != null
                    && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }

            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(results);
                pendingFileCallback = null;
            }
        }
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
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
            }
            downloadReceiver = null;
        }

        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
