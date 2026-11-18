package com.yuvraj.get3

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Android port of the Python/pywebview "Get3" app.
 *  - homeView : local home screen (assets/home.html) with a native bridge  (= pywebview js_api)
 *  - siteView : the real websites (YouTube / Instagram / Facebook / custom) in a mobile-UA WebView
 *               with the floating bolt button injected on every page (assets/overlay.js)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "https://appassets.androidplatform.net/assets/home.html"
    }

    private lateinit var store: Store
    private lateinit var root: FrameLayout
    private lateinit var homeView: WebView
    private lateinit var siteView: WebView
    private lateinit var overlayTemplate: String

    @Volatile
    private var pending: String? = null
    private var atHome = true
    private var clearHistoryOnBlank = false

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        store = Store(applicationContext)
        overlayTemplate = assets.open("overlay.js").bufferedReader().use { it.readText() }

        root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(root)

        setupHome()
        setupSite()

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(siteView, FrameLayout.LayoutParams(match, match))
        root.addView(homeView, FrameLayout.LayoutParams(match, match))
        siteView.visibility = View.GONE
        homeView.loadUrl(HOME_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    // ------------------------------------------------------------ home screen
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupHome() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        homeView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(HomeBridge(), "Get3Native")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                // the home page never navigates away
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = true
            }
        }
    }

    /** Same methods as the Python `Api` class. Runs on a background thread -> UI work is posted. */
    inner class HomeBridge {
        @JavascriptInterface
        fun getState(): String {
            val p = pending
            pending = null
            return store.state(p).toString()
        }

        @JavascriptInterface
        fun openPlatform(id: String): String {
            val p = store.find(id) ?: return ok(false)
            store.record(id)
            runOnUiThread { showSite(p.url) }
            return ok(true)
        }

        @JavascriptInterface
        fun openUrl(raw: String): String {
            val text = raw.trim()
            val low = text.lowercase()
            val looksLikeUrl = text.isNotEmpty() && !text.contains(" ") &&
                    (text.contains(".") || low.startsWith("http://") || low.startsWith("https://"))
            val normalized = if (looksLikeUrl) Validate.normalizeUrl(text) else null
            val target = normalized ?: ("https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8"))
            runOnUiThread { showSite(target) }
            return ok(true)
        }

        @JavascriptInterface
        fun addPlatform(name: String, url: String): String {
            val n = Validate.sanitizeName(name)
            val u = Validate.normalizeUrl(url)
            if (n.isEmpty()) return err("Enter a valid platform name.")
            if (u == null) return err("Enter a valid web address, e.g. twitter.com")
            val p = store.addCustom(n, u)
            runOnUiThread { showSite(p.url) } // auto-focus the new platform
            return JSONObject().put("ok", true).put("id", p.id).put("name", p.name).toString()
        }

        @JavascriptInterface
        fun addSuggested(sid: String): String {
            store.addSuggested(sid)
            return store.state(null).toString()
        }

        @JavascriptInterface
        fun removePlatform(pid: String): String {
            store.remove(pid)
            return store.state(null).toString()
        }

        private fun ok(v: Boolean) = JSONObject().put("ok", v).toString()
        private fun err(m: String) = JSONObject().put("ok", false).put("error", m).toString()
    }

    // ------------------------------------------------------------ website view
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSite() {
        siteView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = MOBILE_UA
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false) // popups stay in the same view
                allowFileAccess = false
                allowContentAccess = false
            }
            webViewClient = SiteClient()
            webChromeClient = SiteChrome()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(siteView, true)
        }
    }

    private inner class SiteClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https", "about" -> false      // everything stays inside the app
                "get3" -> {                            // commands from the floating bolt menu
                    handleCommand(uri)
                    true
                }
                else -> true                           // block intent:, market:, app deep links...
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (url == "about:blank") {
                if (clearHistoryOnBlank) {
                    clearHistoryOnBlank = false
                    view.clearHistory()
                }
                return
            }
            if (!atHome && url != null) injectOverlay()
        }
    }

    private inner class SiteChrome : WebChromeClient() {
        // fullscreen video (YouTube etc.)
        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            if (view == null) return
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }
            view.setBackgroundColor(Color.BLACK)
            customView = view
            customCallback = callback
            val match = ViewGroup.LayoutParams.MATCH_PARENT
            root.addView(view, FrameLayout.LayoutParams(match, match))
            setSystemBars(false)
        }

        override fun onHideCustomView() {
            exitCustomView()
        }

        // file upload (Instagram / Facebook posts, etc.)
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?
        ): Boolean {
            val intent = try {
                fileChooserParams?.createIntent()
            } catch (e: Exception) {
                null
            } ?: return false
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                fileLauncher.launch(intent)
                true
            } catch (e: Exception) {
                fileCallback = null
                false
            }
        }
    }

    private fun exitCustomView() {
        val v = customView ?: return
        customView = null
        root.removeView(v)
        setSystemBars(true)
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    private fun setSystemBars(show: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (show) {
            c.show(WindowInsetsCompat.Type.systemBars())
        } else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ------------------------------------------------------------ navigation
    private fun showSite(url: String) {
        atHome = false
        homeView.visibility = View.GONE
        siteView.visibility = View.VISIBLE
        siteView.loadUrl(url)
    }

    private fun goHome(openAdd: Boolean) {
        atHome = true
        pending = if (openAdd) "add" else null
        clearHistoryOnBlank = true
        siteView.loadUrl("about:blank") // stops any playing video
        siteView.visibility = View.GONE
        homeView.visibility = View.VISIBLE
        homeView.loadUrl(HOME_URL)       // reload = fresh recents / platforms
    }

    private fun handleCommand(uri: Uri) {
        siteView.post {
            when (uri.host) {
                "open" -> {
                    val id = uri.pathSegments.firstOrNull()
                    val p = if (id != null) store.find(id) else null
                    if (p != null) {
                        store.record(p.id)
                        showSite(p.url)
                    }
                }
                "home" -> goHome(false)
                "add" -> goHome(true)
                "back" -> if (siteView.canGoBack()) siteView.goBack()
                else -> {}
            }
        }
    }

    private fun injectOverlay() {
        siteView.evaluateJavascript(overlayTemplate.replace("__DATA__", store.overlayPayload()), null)
    }

    private fun onBack() {
        when {
            customView != null -> exitCustomView()
            !atHome -> if (siteView.canGoBack()) siteView.goBack() else goHome(false)
            else -> homeView.evaluateJavascript(
                "(function(){var s=document.getElementById('scrim');" +
                        "if(s&&s.classList.contains('open')){s.click();return 1}return 0})()"
            ) { r -> if (r != "1") finish() }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush() // keep logins saved
    }

    override fun onDestroy() {
        root.removeAllViews()
        homeView.destroy()
        siteView.destroy()
        super.onDestroy()
    }
}
