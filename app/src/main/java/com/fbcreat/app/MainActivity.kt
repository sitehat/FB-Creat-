package com.fbcreat.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        // WebView সেটিংস (জাভাস্ক্রিপ্ট, ডোম স্টোরেজ এবং কুকিজ পারমিশন এনাবল করা)
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.loadsImagesAutomatically = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // HTML ফাইলের সাথে অ্যান্ড্রয়েড নেটিভ কোডের সংযোগ বা ব্রিজ (JavaScript Interface)
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        // টেলিগ্রাম, হোয়াটসঅ্যাপ এবং এক্সটার্নাল লিংক সেফলি ওপেন করার হ্যান্ডেলার (ক্র্যাশ রোধ করতে try-catch সহ)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("https://t.me/") || url.startsWith("tg://") || url.startsWith("https://wa.me/") || url.startsWith("whatsapp://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        view.loadUrl(url)
                    }
                    return true
                }
                return false
            }
        }

        // WebChromeClient যোগ করা হলো (ফাইল আপলোড, প্রম্পট এবং অ্যালার্ট স্মুথলি কাজ করার জন্য)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                    }
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        // লোকাল অ্যাসেট ফাইল লোড করা
        webView.loadUrl("file:///android_asset/index.html")
    }

    // ফাইল চুজারের রেজাল্ট হ্যান্ডেল করার জন্য
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    // JavaScript Bridge Class (HTML থেকে কল হওয়া ফাংশনগুলোর ব্যাকএন্ড লজিক)
    inner class WebAppInterface(private val mContext: Context) {

        @JavascriptInterface
        fun getRegId(): String {
            // ইউজারের ডিভাইসের জন্য ইউনিক রেজিস্ট্রেশন আইডি রিটার্ন করবে
            return Settings.Secure.getString(mContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "FCB-USER-1024"
        }

        @JavascriptInterface
        fun isAccountActive(): Boolean {
            // সাবস্ক্রিপশন স্ট্যাটাস চেক (ডিফল্টভাবে true রাখা হয়েছে যাতে ইউজার নির্বিধায় ব্যবহার করতে পারে)
            val prefs = mContext.getSharedPreferences("FCB_Prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("is_active", true)
        }

        @JavascriptInterface
        fun copyToClipboardJs(text: String) {
            val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Registration ID", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(mContext, "Registration ID copied!", Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun openFbLite() {
            try {
                val packageName = "com.facebook.lite"
                val intent = mContext.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    mContext.startActivity(intent)
                } else {
                    // ফেসবুক লাইট ইনস্টল না থাকলে প্লে-স্টোরে নিয়ে যাবে
                    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    mContext.startActivity(playIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(mContext, "Could not open Facebook Lite", Toast.LENGTH_SHORT).show()
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
