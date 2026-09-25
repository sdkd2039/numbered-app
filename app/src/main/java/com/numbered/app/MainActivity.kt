package com.numbered.app

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val homeUrl = "https://numbered.casacam.net/NumberedApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.textZoom = 100

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(DownloadBridge(this), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                return handleUrl(view, request.url.toString())
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (url.isNullOrEmpty()) false else handleUrl(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) showOfflinePage()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectDownloadSupport(view)
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "جاري تجهيز الملف للحفظ...", Toast.LENGTH_SHORT).show()
            } else {
                enqueueDownload(url, userAgent, contentDisposition, mimeType)
            }
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl(homeUrl)
    }

    private fun showOfflinePage() {
        val offlineHtml = """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta charset="utf-8">
              <style>
                * { box-sizing: border-box; }
                html, body { margin: 0; width: 100%; height: 100%; }
                body {
                  display: flex; align-items: center; justify-content: center;
                  padding: 24px; background: #f7f8fc; color: #172033;
                  font-family: sans-serif; text-align: center;
                }
                main { width: 100%; max-width: 390px; padding: 36px 24px; }
                .icon {
                  width: 76px; height: 76px; margin: 0 auto 24px;
                  display: flex; align-items: center; justify-content: center;
                  border-radius: 24px; background: #e8edff; color: #3559d8;
                  font-size: 35px; font-weight: bold;
                }
                h1 { margin: 0 0 14px; font-size: 25px; line-height: 1.4; }
                p { margin: 0 auto 28px; color: #667085; font-size: 16px; line-height: 1.8; }
                button {
                  width: 100%; border: 0; border-radius: 14px; padding: 15px;
                  background: #3559d8; color: white; font-size: 16px;
                  font-weight: bold; box-shadow: 0 8px 18px rgba(53,89,216,.22);
                }
                button:active { opacity: .8; }
              </style>
            </head>
            <body><main>
              <div class="icon" aria-hidden="true">⌁</div>
              <h1>التطبيق غير متصل بالإنترنت</h1>
              <p>تحقق من اتصالك بالإنترنت ثم حاول مرة أخرى.</p>
              <button type="button" onclick="AndroidDownloader.retry()">إعادة المحاولة</button>
            </main></body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(homeUrl, offlineHtml, "text/html", "UTF-8", homeUrl)
    }

    private fun handleUrl(view: WebView?, url: String): Boolean {
        if (url.startsWith("intent:", ignoreCase = true)) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                try { startActivity(intent) }
                catch (_: Exception) { intent.getStringExtra("browser_fallback_url")?.let { view?.loadUrl(it) } }
            } catch (_: URISyntaxException) { }
            return true
        }

        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host?.lowercase() ?: ""
        val isX = host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com") ||
            scheme == "x" || scheme == "twitter"
        val isWhatsApp = scheme == "whatsapp" || host == "wa.me" || host.endsWith(".whatsapp.com")
        val isTelegram = scheme == "tg" || scheme == "telegram" || host == "t.me" || host.endsWith(".telegram.me")
        val isPhoneOrMail = scheme == "tel" || scheme == "mailto"

        if (isX || isWhatsApp || isTelegram || isPhoneOrMail) {
            openExternal(uri, url, isX)
            return true
        }
        if (scheme != "http" && scheme != "https") {
            openExternal(uri, url, false)
            return true
        }
        return false
    }

    private fun openExternal(uri: Uri, originalUrl: String, isX: Boolean) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: Exception) {
            val fallback = when {
                originalUrl.startsWith("x:", true) || originalUrl.startsWith("twitter:", true) ->
                    Uri.parse("https://x.com/" + originalUrl.substringAfter(':').trimStart('/'))
                isX && (uri.scheme == "http" || uri.scheme == "https") -> uri
                uri.scheme == "http" || uri.scheme == "https" -> uri
                else -> null
            }
            if (fallback != null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, fallback).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch (_: Exception) { Toast.makeText(this, "لا يمكن فتح هذا الرابط", Toast.LENGTH_SHORT).show() }
            } else if (!isX) Toast.makeText(this, "التطبيق المطلوب غير متوفر", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueueDownload(url: String, userAgent: String?, disposition: String?, mimeType: String?) {
        try {
            val fileName = URLUtil.guessFileName(url, disposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
            if (!mimeType.isNullOrEmpty()) request.setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            if (!userAgent.isNullOrEmpty()) request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("جاري تنزيل الملف...")
            request.setTitle(fileName)
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "بدأ تحميل: $fileName", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "تعذر بدء التحميل", Toast.LENGTH_SHORT).show() }
    }

    private fun injectDownloadSupport(view: WebView?) {
        if (view == null) return
        val javascript = """
            (function() {
              if (window.__androidDownloadSupportInstalled) return;
              window.__androidDownloadSupportInstalled = true;
              function save(url, name, type) {
                fetch(url, {credentials:'include'}).then(function(r) {
                  if (!r.ok) throw new Error('HTTP '+r.status); return r.blob();
                }).then(function(blob) {
                  var filename = name || 'download';
                  var mime = type || blob.type || 'application/octet-stream';
                  AndroidDownloader.startFile(filename, mime);
                  var size = 256 * 1024, position = 0;
                  function next() {
                    if (position >= blob.size) { AndroidDownloader.finishFile(); return; }
                    var reader = new FileReader();
                    reader.onload = function() {
                      AndroidDownloader.appendBase64(reader.result.substring(reader.result.indexOf(',') + 1));
                      position += size; next();
                    };
                    reader.readAsDataURL(blob.slice(position, position + size));
                  }
                  next();
                }).catch(function(e) { console.log('Download error:', e); });
              }
              document.addEventListener('click', function(event) {
                var a = event.target;
                while (a && a.tagName !== 'A') a = a.parentElement;
                if (!a) return;
                var href = a.getAttribute('href');
                if (!href || (!href.startsWith('blob:') && !href.startsWith('data:')) || !a.hasAttribute('download')) return;
                event.preventDefault(); event.stopPropagation(); save(href, a.getAttribute('download'), a.getAttribute('type'));
              }, true);
            })();
        """.trimIndent()
        view.evaluateJavascript(javascript, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState); super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }

    override fun onDestroy() {
        webView.stopLoading(); webView.destroy(); super.onDestroy()
    }

    class DownloadBridge(private val context: Context) {
        private var name = "download"
        private var mime = "application/octet-stream"
        private var output = ByteArrayOutputStream()
        private var videoUri: Uri? = null
        private var videoOutput: OutputStream? = null

        @Synchronized
        @JavascriptInterface
        fun retry() {
            val activity = context as? MainActivity ?: return
            activity.runOnUiThread {
                activity.webView.loadUrl(activity.homeUrl)
            }
        }

        @Synchronized
        @JavascriptInterface
        fun startFile(fileName: String, mimeType: String) {
            cleanupVideo()
            name = fileName.substringAfterLast('/').ifBlank { "download" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            mime = mimeType.substringBefore(';').ifBlank { "application/octet-stream" }
            output = ByteArrayOutputStream()
            if (mime.startsWith("video/", true)) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                videoUri = context.contentResolver.insert(collection, values)
                videoOutput = videoUri?.let { context.contentResolver.openOutputStream(it) }
                if (videoUri == null || videoOutput == null) { cleanupVideo(); throw IllegalStateException("Cannot create video") }
            }
        }

        @Synchronized
        @JavascriptInterface
        fun appendBase64(chunk: String) {
            val bytes = Base64.decode(chunk, Base64.DEFAULT)
            if (videoOutput != null) videoOutput?.write(bytes) else output.write(bytes)
        }

        @Synchronized
        @JavascriptInterface
        fun finishFile() {
            val currentVideo = videoUri
            try {
                if (currentVideo != null) {
                    videoOutput?.close(); videoOutput = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                        context.contentResolver.update(currentVideo, values, null, null)
                    }
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val uri = context.contentResolver.insert(collection, values) ?: throw IllegalStateException("Cannot create download")
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(output.toByteArray()) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                            context.contentResolver.update(uri, values, null, null)
                        }
                    } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
                }
                Toast.makeText(context, "تم حفظ الملف: $name", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                currentVideo?.let { context.contentResolver.delete(it, null, null) }
                Toast.makeText(context, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show()
            } finally { output.reset(); videoUri = null; videoOutput = null }
        }

        private fun cleanupVideo() {
            try { videoOutput?.close() } catch (_: Exception) { }
            videoUri?.let { context.contentResolver.delete(it, null, null) }
            videoUri = null; videoOutput = null
        }
    }
}
