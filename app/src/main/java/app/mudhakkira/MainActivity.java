package app.mudhakkira;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.*;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** يغلّف تطبيق الويب (assets/index.html) داخل WebView مع دعم الطباعة والتصدير والاستيراد. */
public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> filePathCb;
    private static final int REQ_FILE = 1;

    // يعوّض عن a[download] وطباعة window.print غير المدعومتين في WebView
    private static final String JS_PATCH =
        "(function(){if(window.__patched)return;window.__patched=true;" +
        "window.print=function(){Android.print();};" +
        "var oc=HTMLAnchorElement.prototype.click;" +
        "HTMLAnchorElement.prototype.click=function(){" +
        " if(this.hasAttribute('download')&&this.href.indexOf('blob:')===0){" +
        "  var n=this.download,h=this.href;" +
        "  fetch(h).then(function(r){return r.blob()}).then(function(b){" +
        "   var f=new FileReader();f.onload=function(){Android.save(n,f.result.split(',')[1],b.type||'application/octet-stream')};f.readAsDataURL(b)});" +
        "  return;}" +
        " return oc.apply(this,arguments)};})();";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(JS_PATCH, null); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme()) || "about".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                    .setPositiveButton("حسنًا", (d, w) -> r.confirm()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                    .setPositiveButton("نعم", (d, w) -> r.confirm())
                    .setNegativeButton("إلغاء", (d, w) -> r.cancel()).show();
                return true;
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try { startActivityForResult(p.createIntent(), REQ_FILE); }
                catch (Exception e) { filePathCb = null; return false; }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && filePathCb != null) {
            filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePathCb = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override public void onBackPressed() {
        // العودة للرئيسية داخل التطبيق ثم الخروج
        web.evaluateJavascript("(function(){var h=document.querySelector('nav button[data-t=home]');" +
            "if(h&&!h.classList.contains('on')){h.click();return 1}return 0})()", v -> {
            if (!"1".equals(v)) finish();
        });
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    private class Bridge {
        @JavascriptInterface public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                PrintDocumentAdapter a = web.createPrintDocumentAdapter("المذكرة اليومية");
                pm.print("المذكرة اليومية", a, new PrintAttributes.Builder().build());
            });
        }
        @JavascriptInterface public void save(String name, String b64, String mime) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                OutputStream os;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    os = getContentResolver().openOutputStream(u);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    os = new FileOutputStream(new File(dir, name));
                }
                os.write(bytes); os.close();
                toast("تم الحفظ في التنزيلات: " + name);
            } catch (Exception e) { toast("تعذّر الحفظ: " + e.getMessage()); }
        }
        private void toast(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show()); }
    }
}
