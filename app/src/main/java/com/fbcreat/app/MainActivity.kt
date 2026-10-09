package com.fbcreat.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true

        // জাভাস্ক্রিপ্ট ব্রিজ যুক্ত করা হলো
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    if (url.contains("whatsapp") || url.contains("t.me") || url.contains("telegram")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("file:///android_asset/index.html")
    }

    class WebAppInterface(private val mContext: MainActivity) {
        @JavascriptInterface
        fun getRegId(): String {
            // ইউজারের নিজস্ব রেজিস্টার্ড আইডি এখানে থাকবে
            return "FCB-REG-12345"
        }

        @JavascriptInterface
        fun openFbLite() {
            try {
                val intent = mContext.packageManager.getLaunchIntentForPackage("com.facebook.lite")
                if (intent != null) {
                    mContext.startActivity(intent)
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lite.facebook.com"))
                    mContext.startActivity(browserIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
