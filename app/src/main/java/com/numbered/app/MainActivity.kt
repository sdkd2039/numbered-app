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
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URISyntaxException
import android.util.Base64

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

        // الحفاظ على حجم الموقع الطبيعي
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false

        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        webSettings.textZoom = 100

        // السماح بالكوكيز
        val cookieManager =
            CookieManager.getInstance()

        cookieManager.setAcceptCookie(true)

        cookieManager.setAcceptThirdPartyCookies(
            webView,
            true
        )

        // =========================================================
        // جسر تحميل الصور / الفيديو / الصوت من blob و data
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

                        } catch (
                            e: URISyntaxException
                        ) {
                            // الرابط غير صالح
                        }

                        return true
                    }

                    // =================================================
                    // تحليل الرابط
                    // =================================================

                    val uri =
                        Uri.parse(url)

                    val host =
                        uri.host
                            ?.lowercase()
                            ?: ""

                    // =================================================
                    // منصة X / Twitter
                    // =================================================

                    val isX =
                        host == "x.com" ||
                        host.endsWith(".x.com") ||
                        host == "twitter.com" ||
                        host.endsWith(".twitter.com") ||
                        url.startsWith("x:") ||
                        url.startsWith("twitter:")

                    // =================================================
                    // واتساب / تيليجرام
                    // =================================================

                    val isWhatsApp =
                        url.startsWith("whatsapp:")

                    val isTelegram =
                        url.startsWith("tg:") ||
                        url.startsWith("telegram:")

                    // =================================================
                    // الهاتف والبريد
                    // =================================================

                    val isPhoneOrMail =
                        url.startsWith("tel:") ||
                        url.startsWith("mailto:")

                    // =================================================
                    // روابط خارجية
                    // =================================================

                    if (
                        isX ||
                        isWhatsApp ||
                        isTelegram ||
                        isPhoneOrMail
                    ) {

                        try {

                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    uri
                                )

                            startActivity(intent)

                            return true

                        } catch (e: Exception) {

                            Toast.makeText(
                                this@MainActivity,
                                "التطبيق المطلوب غير متوفر، سيتم فتح الرابط خارجيًا",
                                Toast.LENGTH_SHORT
                            ).show()

                            // محاولة فتح الرابط في المتصفح
                            try {

                                val browserIntent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)
                                    )

                                startActivity(
                                    browserIntent
                                )

                            } catch (ignored: Exception) {
                            }

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
                                    uri
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
                    // روابط الموقع تبقى داخل WebView
                    // =================================================

                    return false
                }

                // =====================================================
                // بعد تحميل الصفحة
                // =====================================================

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    injectDownloadSupport(
                        view
                    )
                }
            }

        // =========================================================
        // DownloadManager للملفات العادية
        // =========================================================

        webView.setDownloadListener {
                url,
                userAgent,
                contentDisposition,
                mimetype,
                _ ->

            // =====================================================
            // blob / data
            // =====================================================

            if (
                url.startsWith("blob:") ||
                url.startsWith("data:")
            ) {

                // تتم معالجتها بواسطة JavaScript
                Toast.makeText(
                    applicationContext,
                    "جاري تجهيز الملف للحفظ...",
                    Toast.LENGTH_SHORT
                ).show()

                return@setDownloadListener
            }

            // =====================================================
            // تحميل عادي
            // =====================================================

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

                if (
                    !mimetype.isNullOrEmpty()
                ) {

                    request.setMimeType(
                        mimetype
                    )
                }

                // الكوكيز
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

                // User-Agent
                if (
                    !userAgent.isNullOrEmpty()
                ) {

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
                // محاولة فتح الملف خارجيًا
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
        // تحميل الصفحة
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
    // إضافة دعم تحميل blob / data
    // =============================================================

    private fun injectDownloadSupport(
        view: WebView?
    ) {

        if (view == null) {
            return
        }

        val javascript = """
            (function() {

                if (window.__androidDownloadSupportInstalled) {
                    return;
                }

                window.__androidDownloadSupportInstalled = true;

                function sendFile(url, fileName, mimeType) {

                    try {

                        fetch(url)
                            .then(function(response) {
                                return response.blob();
                            })
                            .then(function(blob) {

                                var reader =
                                    new FileReader();

                                reader.onloadend =
                                    function() {

                                        try {

                                            AndroidDownloader.saveBase64File(
                                                reader.result,
                                                fileName || "download",
                                                mimeType || blob.type || "application/octet-stream"
                                            );

                                        } catch (e) {
                                            console.log(e);
                                        }
                                    };

                                reader.readAsDataURL(blob);

                            })
                            .catch(function(error) {
                                console.log(
                                    "Download error:",
                                    error
                                );
                            });

                    } catch (e) {
                        console.log(e);
                    }
                }

                document.addEventListener(
                    "click",
                    function(event) {

                        var element =
                            event.target;

                        while (
                            element &&
                            element.tagName !== "A"
                        ) {
                            element =
                                element.parentElement;
                        }

                        if (!element) {
                            return;
                        }

                        var href =
                            element.getAttribute("href");

                        if (!href) {
                            return;
                        }

                        var isBlob =
                            href.indexOf("blob:") === 0;

                        var isData =
                            href.indexOf("data:") === 0;

                        var hasDownload =
                            element.hasAttribute("download");

                        if (
                            (isBlob || isData) &&
                            hasDownload
                        ) {

                            event.preventDefault();
                            event.stopPropagation();

                            var fileName =
                                element.getAttribute("download") ||
                                "download";

                            var mimeType =
                                element.getAttribute("type") ||
                                "application/octet-stream";

                            sendFile(
                                href,
                                fileName,
                                mimeType
                            );
                        }

                    },
                    true
                );

            })();
        """.trimIndent()

        view.evaluateJavascript(
            javascript,
            null
        )
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
    // العودة من X / واتساب / التطبيقات الخارجية
    // =============================================================

    override fun onResume() {

        super.onResume()

        // لا نعيد تحميل الموقع.
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
        webView.destroy()

        super.onDestroy()
    }

    // =============================================================
    // جسر JavaScript -> Android
    // =============================================================

    class DownloadBridge(
        private val context: Context
    ) {

        @JavascriptInterface
        fun saveBase64File(
            dataUrl: String,
            fileName: String,
            mimeType: String
        ) {

            try {

                val commaIndex =
                    dataUrl.indexOf(",")

                if (commaIndex == -1) {
           