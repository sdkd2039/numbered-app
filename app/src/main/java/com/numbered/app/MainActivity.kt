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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val homeUrl =
        "https://numbered.casacam.net/NumberedApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // =========================================================
        // إعداد WebView
        // =========================================================

        val webSettings: WebSettings = webView.settings

        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true

        webSettings.cacheMode =
            WebSettings.LOAD_DEFAULT

        // =========================================================
        // حجم الموقع
        // =========================================================

        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false

        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        webSettings.textZoom = 100

        // =========================================================
        // الكوكيز
        // =========================================================

        val cookieManager =
            CookieManager.getInstance()

        cookieManager.setAcceptCookie(true)

        cookieManager.setAcceptThirdPartyCookies(
            webView,
            true
        )

        // =========================================================
        // جسر JavaScript لتحميل blob / data
        // =========================================================

        webView.addJavascriptInterface(
            DownloadBridge(this),
            "AndroidDownloader"
        )

        // =========================================================
        // WebView Client
        // =========================================================

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?
                ): Boolean {

                    if (url.isNullOrEmpty()) {
                        return false
                    }

                    // =================================================
                    // روابط intent://
                    // =================================================

                    if (url.startsWith("intent:")) {

                        try {

                            val intent =
                                Intent.parseUri(
                                    url,
                                    Intent.URI_INTENT_SCHEME
                                )

                            try {

                                startActivity(intent)

                                return true

                            } catch (e: Exception) {

                                val fallbackUrl =
                                    intent.getStringExtra(
                                        "browser_fallback_url"
                                    )

                                if (!fallbackUrl.isNullOrEmpty()) {

                                    view?.loadUrl(
                                        fallbackUrl
                                    )

                                    return true
                                }
                            }

                        } catch (e: URISyntaxException) {
                            // تجاهل الرابط غير الصالح
                        }

                        return true
                    }

                    // =================================================
                    // تحليل الرابط
                    // =================================================

                    val uri =
                        Uri.parse(url)

                    val host =
                       