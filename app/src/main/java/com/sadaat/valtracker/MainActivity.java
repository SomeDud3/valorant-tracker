package com.sadaat.valtracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0f1923"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f1923"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "NativeHttp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack ? appBack() : false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    class Bridge {
        @JavascriptInterface
        public void get(final int id, final String url, final String key) {
            new Thread(() -> {
                int status = 0;
                String body;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "ValTracker-Android/1.0");
                    if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", key);
                    status = c.getResponseCode();
                    InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        in.close();
                    }
                    body = out.toString("UTF-8");
                } catch (Exception e) {
                    body = "{\"errors\":[{\"message\":" + JSONObject.quote(String.valueOf(e.getMessage())) + "}]}";
                }
                final String js = "window.__nativeCb(" + id + "," + status + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }
}
