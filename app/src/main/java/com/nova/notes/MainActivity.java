package com.nova.notes;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;
import android.util.Base64;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_PICKER = 1001;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ReminderScheduler.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1002);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager am = (android.app.AlarmManager)getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            }
        }
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new FileBridge(), "AndroidFiles");
        webView.addJavascriptInterface(new ReminderBridge(), "AndroidReminders");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(Intent.createChooser(intent, "NOVA Notes yedeği seç"), FILE_PICKER);
                return true;
            }
            @Override public void onProgressChanged(WebView view, int progress) { setTitle("NOVA Notes"); }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }
    private class ReminderBridge {
        @JavascriptInterface public void sync(String json) {
            try { ReminderScheduler.sync(MainActivity.this, json); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Hatırlatıcılar Android'e aktarılamadı", Toast.LENGTH_LONG).show()); }
        }
    }
    private class FileBridge {
        @JavascriptInterface public void save(String data, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name == null ? "nova-notes-yedek.json" : name);
                values.put(MediaStore.Downloads.MIME_TYPE, mime == null ? "application/json" : mime);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Dosya oluşturulamadı");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(bytes); }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Yedek İndirilenler klasörüne kaydedildi", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Yedek kaydedilemedi", Toast.LENGTH_LONG).show());
            }
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}