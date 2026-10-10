package com.fbcreat.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var nativeFloatingContainer: FrameLayout
    private lateinit var nativeToolsMenu: LinearLayout
    private lateinit var nativeToggleBtn: Button
    private var isMenuOpen = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        nativeFloatingContainer = findViewById(R.id.nativeFloatingContainer)
        nativeToolsMenu = findViewById(R.id.nativeToolsMenu)
        nativeToggleBtn = findViewById(R.id.nativeToggleBtn)

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // JavaScript থেকে অ্যান্ড্রয়েড কল করার ব্রিজ
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                
                // টেলিগ্রাম বা অন্যান্য কাস্টম স্কিম (tg://, sfilvavs:// ইত্যাদি) এক্সটার্নাল অ্যাপে ওপেন করবে
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                
                // টেলিগ্রাম ওয়েব লিংক সরাসরি হ্যান্ডেল করা
                if (url.contains("t.me/")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        // অ্যাপ না থাকলে WebView-তেই ওপেন করবে
                    }
                }

                view.loadUrl(url)
                return true
            }
        }

        // প্লাস/মাইনাস টগল বাটন ক্লিক
        nativeToggleBtn.setOnClickListener {
            isMenuOpen = !isMenuOpen
            if (isMenuOpen) {
                nativeToolsMenu.visibility = View.VISIBLE
                nativeToggleBtn.text = "-"
            } else {
                nativeToolsMenu.visibility = View.GONE
                nativeToggleBtn.text = "+"
            }
        }

        // শর্টকাট টুলস বাটনগুলোর অ্যাকশন
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            webView.evaluateJavascript("autoExtractProfileId();", null)
        }
        findViewById<Button>(R.id.btn2FA).setOnClickListener {
            webView.evaluateJavascript("autoExtractAndGen2FA();", null)
        }
        findViewById<Button>(R.id.btnCookies).setOnClickListener {
            webView.evaluateJavascript("extractCookies();", null)
        }
        findViewById<Button>(R.id.btnCode).setOnClickListener {
            webView.evaluateJavascript("getVerificationCode();", null)
        }
        findViewById<Button>(R.id.btnClearData).setOnClickListener {
            webView.evaluateJavascript("clearAppData();", null)
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun loadFacebook(url: String) {
            runOnUiThread {
                nativeFloatingContainer.visibility = View.VISIBLE
                webView.loadUrl(url)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
