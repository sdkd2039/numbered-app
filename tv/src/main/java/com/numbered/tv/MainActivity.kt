
package com.sutstudio.numberedtv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                applyHomeChannelDestination()
            }
        }
        webView.webChromeClient = WebChromeClient()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            applyHomeChannelDestination()
        } else {
            webView.loadUrl("https://numbered.casacam.net/NumberedTV1.html")
        }
    }


    /**
     * يوجّه بطاقة Android TV إلى القسم والفئة المطلوبة داخل صفحة الموقع.
     * ننتظر تحميل بيانات الصفحة قبل استدعاء دوالها حتى لا نرجع إلى الفئة الافتراضية.
     */
    private fun applyHomeChannelDestination() {
        val type = intent?.getStringExtra(
            com.sutstudio.numberedtv.channels.HomeChannels.EXTRA_CONTENT_TYPE
        ) ?: return
        val contentId = intent?.getStringExtra(
            com.sutstudio.numberedtv.channels.HomeChannels.EXTRA_CONTENT_ID
        ) ?: return
        val encodedType = org.json.JSONObject.quote(type)
        val encodedId = org.json.JSONObject.quote(contentId)
        val script = """
            (function() {
              var type = $encodedType;
              var id = $encodedId;
              var attempts = 0;
              var timer = setInterval(function() {
                attempts++;
                if (typeof switchTab !== 'function') {
                  if (attempts > 40) clearInterval(timer);
                  return;
                }
                if (type === 'dhikr') {
                  if (!Array.isArray(dhikrData) || dhikrData.length === 0) {
                    if (attempts > 40) clearInterval(timer);
                    return;
                  }
                  clearInterval(timer);
                  switchTab('dhikr');
                  var buttons = Array.from(document.querySelectorAll('.sub-tabs .sub-tab-btn'));
                  var selected = buttons.find(function(b) {
                    return b.textContent.trim().replace(/ـ/g, '') === id.replace(/ـ/g, '');
                  });
                  if (selected) showDhikr(id, selected);
                  document.getElementById('panel-dhikr').scrollIntoView();
                } else if (type === 'remaining') {
                  clearInterval(timer);
                  switchTab('remaining');
                  var name = id.substring(id.indexOf(':') + 1);
                  var items = Array.from(document.querySelectorAll('#panel-remaining .countdown-item'));
                  var target = items.find(function(item) {
                    return item.querySelector('.countdown-title')?.textContent.trim() === name;
                  });
                  if (target) {
                    target.scrollIntoView({behavior: 'smooth', block: 'center'});
                    target.focus();
                  }
                }
              }, 250);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::webView.isInitialized) applyHomeChannelDestination()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}
