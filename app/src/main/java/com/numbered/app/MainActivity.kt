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
        // ضبط حجم الموقع ليظهر بحجمه الطبيعي على شاشة الجوال
        // =========================================================

        // لا نفرض تصغير الصفحة أو تكبيرها
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false

        // منع تكبير WebView
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        // المحافظة على حجم النص الأصلي للموقع
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

                        } catch (
                            e: URISyntaxException
                        ) {
                            // تجاهل الرابط غير الصالح
                        }

                        return true
                    }

                    // =================================================
                    // الروابط الخارجية
                    // =================================================

                    val externalLink =
                        url.startsWith("whatsapp:") ||
                        url.startsWith("tg:") ||
                        url.startsWith("x:") ||
                        url.startsWith("twitter:") ||
                        url.startsWith("tel:") ||
                        url.startsWith("mailto:") ||
                        url.contains("twitter.com/intent") ||
                        url.contains("x.com/intent")

                    if (externalLink) {

                        try {

                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url)
                                )

                            startActivity(intent)

                            return true

                        } catch (e: Exception) {

                            Toast.makeText(
                                this@MainActivity,
                                "التطبيق المطلوب غير متوفر",
                                Toast.LENGTH_SHORT
                            ).show()

                            return true
                        }
                    }

                    // =================================================
                    // أي بروتوكول غير HTTP / HTTPS
                    // =================================================

                    if (
                        !url.startsWith("http://") &&
                        !url.startsWith("https://")
                    ) {

                        try {

                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url)
                                )

                            startActivity(intent)

                            return true

                        } catch (e: Exception) {

                            Toast.makeText(
                                this@MainActivity,
                                "لا يمكن فتح هذا الرابط",
                                Toast.LENGTH_SHORT
                            ).show()

                            return true
                        }
                    }

                    // =================================================
                    // روابط الموقع نفسها
                    // تبقى داخل WebView
                    // =================================================

                    return false
                }
            }

        // =========================================================
        // نظام تحميل الملفات
        // =========================================================

        webView.setDownloadListener {
                url,
                userAgent,
                contentDisposition,
                mimetype,
                _ ->

            try {

                val fileName =
                    URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimetype
                    )

                val request =
                    DownloadManager.Request(
                        Uri.parse(url)
                    )

                // نوع الملف
                if (!mimetype.isNullOrEmpty()) {

                    request.setMimeType(
                        mimetype
                    )
                }

                // =================================================
                // الكوكيز
                // =================================================

                val cookies =
                    CookieManager
                        .getInstance()
                        .getCookie(url)

                if (!cookies.isNullOrEmpty()) {

                    request.addRequestHeader(
                        "Cookie",
                        cookies
                    )
                }

                // =================================================
                // User-Agent
                // =================================================

                if (!userAgent.isNullOrEmpty()) {

                    request.addRequestHeader(
                        "User-Agent",
                        userAgent
                    )
                }

                request.setDescription(
                    "جاري تنزيل الملف..."
                )

                request.setTitle(
                    fileName
                )

                request.allowScanningByMediaScanner()

                request.setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

                // =================================================
                // حفظ الملف داخل مجلد Downloads
                // =================================================

                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )

                val downloadManager =
                    getSystemService(
                        Context.DOWNLOAD_SERVICE
                    ) as DownloadManager

                downloadManager.enqueue(
                    request
                )

                Toast.makeText(
                    applicationContext,
                    "بدأ تحميل: $fileName",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                // =================================================
                // حل احتياطي
                // =================================================

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )

                    startActivity(intent)

                } catch (ex: Exception) {

                    Toast.makeText(
                        applicationContext,
                        "تعذر بدء التحميل",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // =========================================================
        // استعادة الموقع بدلاً من إعادة تشغيله
        // =========================================================

        if (savedInstanceState != null) {

            webView.restoreState(
                savedInstanceState
            )

        } else {

            webView.loadUrl(
                homeUrl
            )
        }
    }

    // =============================================================
    // حفظ حالة الموقع
    // =============================================================

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        webView.saveState(
            outState
        )

        super.onSaveInstanceState(
            outState
        )
    }

    // =============================================================
    // العودة من واتساب / X / التطبيقات الخارجية
    // =============================================================

    override fun onResume() {
        super.onResume()

        // مهم:
        // لا نعيد تحميل الموقع هنا.
        //
        // لذلك عند الرجوع من واتساب أو X
        // يبقى المستخدم في نفس الصفحة والمكان.
    }

    // =============================================================
    // زر الرجوع
    // =============================================================

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        if (webView.canGoBack()) {

            webView.goBack()

        } else {

            super.onBackPressed()
        }
    }

    // =============================================================
    // تنظيف WebView
    // =============================================================

    override fun onDestroy() {

        webView.stopLoading()

        webView.webViewClient = null

        webView.destroy()

        super.onDestroy()
    }
}