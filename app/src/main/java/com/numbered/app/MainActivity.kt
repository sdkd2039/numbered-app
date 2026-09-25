package com.numbered.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false

                // التعامل مع روابط الـ intent بدقة باستخدام Intent.parseUri
                if (url.startsWith("intent:")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (intent != null) {
                            try {
                                startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                if (fallbackUrl != null) {
                                    view?.loadUrl(fallbackUrl)
                                    return true
                                }
                            }
                        }
                    } catch (e: URISyntaxException) {
                        // تجاهل الخطأ في حال تعذر التحليل
                    }
                }

                // التعامل مع الروابط والتطبيقات الخارجية (واتساب، تيليجرام، إكس، تويتر، هاتف، إيميل، وغيرها)
                if (url.startsWith("whatsapp:") || url.startsWith("tg:") || 
                    url.startsWith("x:") || url.startsWith("twitter:") || 
                    url.startsWith("tel:") || url.startsWith("mailto:") || 
                    (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "التطبيق المطلوب غير متوفر على الجهاز", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }

                // الروابط العادية تبقى داخل الـ WebView
                view?.loadUrl(url)
                return true
            }
        }

        // تفعيل التحميل الفعلي للملفات باستخدام DownloadManager
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)

                request.setMimeType(mimetype)
                val cookies = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("جاري تنزيل الملف...")
                request.setTitle(fileName)
                request.allowScanningByMediaScanner()
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)

                Toast.makeText(applicationContext, "بدء تحميل: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "تعذر بدء التحميل", Toast.LENGTH_SHORT).show()
            }
        }

        // تحميل الرابط الأساسي للموقع
        webView.loadUrl("https://numbered.casacam.net/NumberedApp")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
