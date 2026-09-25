package com.numbered.app

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إنشاء الـ WebView كعنصر رئيسي في الواجهة
        webView = WebView(this)
        setContentView(webView)

        // إعدادات المتصفح للتعامل مع رابط الصفحة بشكل متكامل
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        
        // حل مشكلة ERR_CACHE_MISS بالسماح بالتمرير والتحديث التلقائي للكاش
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    view?.loadUrl(url)
                }
                return true
            }
        }

        // تحميل الرابط المباشر
        webView.loadUrl("https://numbered.casacam.net/NumberedApp")
    }

    // السماح بالرجوع للخلف داخل صفحات الموقع عند إغلاق التطبيق
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
