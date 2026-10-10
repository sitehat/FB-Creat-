package com.fbcreat.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer

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
        webSettings.setSupportMultipleWindows(true)
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        // ফেসবুক সেটিংস ও পপআপ উইন্ডো ক্র্যাশ রোধ করার নিরাপদ WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                try {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        val dummyWebView = WebView(this@MainActivity)
                        dummyWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString()
                                if (!url.isNullOrEmpty()) {
                                    webView.loadUrl(url)
                                }
                                return true
                            }
                        }
                        transport.webView = dummyWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                
                // টেলিগ্রাম বা কাস্টম স্কিম হ্যান্ডেল করা
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                
                if (url.contains("t.me/") || url.contains("telegram.me")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {}
                }

                return false
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
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

        // টুলস বাটনগুলোর নেটিভ অ্যাকশন
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            extractProfileId()
            toggleMenu()
        }
        findViewById<Button>(R.id.btn2FA).setOnClickListener {
            extractAndGen2FA()
            toggleMenu()
        }
        findViewById<Button>(R.id.btnCookies).setOnClickListener {
            extractCookies()
            toggleMenu()
        }
        findViewById<Button>(R.id.btnCode).setOnClickListener {
            getVerificationCode()
            toggleMenu()
        }
        findViewById<Button>(R.id.btnClearData).setOnClickListener {
            clearFacebookDataOnly()
            toggleMenu()
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun toggleMenu() {
        isMenuOpen = false
        nativeToolsMenu.visibility = View.GONE
        nativeToggleBtn.text = "+"
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

    // --- নেটিভ টুলস ফাংশনসমূহ ---

    private fun extractProfileId() {
        val url = webView.url ?: ""
        val regex = "(?:id=|profile\\.php\\?id=|\\/)([0-9]{5,})".toRegex()
        val match = regex.find(url)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        if (match != null) {
            val profileId = match.groupValues[1]
            clipboard.setPrimaryClip(ClipData.newPlainText("Profile ID", profileId))
            Toast.makeText(this, "✅ Profile ID Copied: $profileId", Toast.LENGTH_SHORT).show()
        } else {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                val clipMatch = regex.find(clipText)
                if (clipMatch != null) {
                    val profileId = clipMatch.groupValues[1]
                    clipboard.setPrimaryClip(ClipData.newPlainText("Profile ID", profileId))
                    Toast.makeText(this, "✅ Profile ID Copied: $profileId", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(this, "⚠️ Profile ID not found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAndGen2FA() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        var secretKey = ""
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val clipText = clipData.getItemAt(0).text?.toString() ?: ""
            val cleanText = clipText.replace("\\s+".toRegex(), "").toUpperCase()
            val match = "[A-Z2-7]{16,32}".toRegex().find(cleanText)
            if (match != null) {
                secretKey = match.value
            }
        }
        
        if (secretKey.isNotEmpty()) {
            process2FA(secretKey)
        } else {
            val input = EditText(this)
            input.hint = "Secret Key দিন"
            input.setPadding(40, 40, 40, 40)
            AlertDialog.Builder(this)
                .setTitle("🔑 2FA Secret Key")
                .setView(input)
                .setPositiveButton("জেনারেট") { _, _ ->
                    val manualKey = input.text.toString().trim()
                    if (manualKey.isNotEmpty()) {
                        process2FA(manualKey)
                    } else {
                        Toast.makeText(this, "⚠️ সিক্রেট কী খালি রাখা যাবে না!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }
    }

    private fun process2FA(secretKey: String) {
        val code = generateTOTP(secretKey)
        if (code != null) {
            val result = "$secretKey | $code"
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", result))
            Toast.makeText(this, "✅ 2FA Copied!\nCode: $code", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "⚠️ ভুল বা অবৈধ সিক্রেট কী!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateTOTP(secretKey: String): String? {
        try {
            val cleanKey = secretKey.replace("\\s+".toRegex(), "").toUpperCase()
            val bytes = base32Decode(cleanKey) ?: return null
            val timeStep = 30L
            val currentTime = System.currentTimeMillis() / 1000L
            val counter = currentTime / timeStep
            
            val data = ByteBuffer.allocate(8).putLong(counter).array()
            val signKey = SecretKeySpec(bytes, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(signKey)
            val hash = mac.doFinal(data)
            
            val offset = (hash[hash.size - 1].toInt() and 0xf)
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                         ((hash[offset + 1].toInt() and 0xff) shl 16) or
                         ((hash[offset + 2].toInt() and 0xff) shl 8) or
                         (hash[offset + 3].toInt() and 0xff)
                         
            val otp = binary % 1_000_000
            return String.format("%06d", otp)
        } catch (e: Exception) {
            return null
        }
    }

    private fun base32Decode(base32: String): ByteArray? {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()
        
        for (char in base32) {
            if (char == '=') break
            val valIndex = base32Chars.indexOf(char)
            if (valIndex < 0) return null
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    private fun extractCookies() {
        val url = webView.url ?: "https://m.facebook.com"
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url) ?: ""
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        if (cookies.contains("c_user") || cookies.contains("xs")) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Cookies", cookies))
            Toast.makeText(this, "✅ Cookies Copied!", Toast.LENGTH_SHORT).show()
        } else {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                if (clipText.contains("c_user") || clipText.contains("xs")) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Cookies", clipText))
                    Toast.makeText(this, "✅ Cookies Copied!", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(this, "⚠️ No Cookies found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getVerificationCode() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            val regex = "\\b\\d{5,6}\\b".toRegex()
            val match = regex.find(text)
            if (match != null) {
                val code = match.value
                clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", code))
                Toast.makeText(this, "✅ Code Copied: $code", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "⚠️ Code not found in clipboard!", Toast.LENGTH_SHORT).show()
    }

    // --- শুধুমাত্র ফেসবুকের ক্যাশ ও কুকিজ ক্লিয়ার করার ফাংশন (রেজিস্ট্রেশন আইডি সুরক্ষিত থাকবে) ---
    private fun clearFacebookDataOnly() {
        AlertDialog.Builder(this)
            .setTitle("Clear Facebook Cache")
            .setMessage("আপনি কি শুধুমাত্র ফেসবুকের ক্যাশ ও কুকিজ ক্লিয়ার করতে চান? (আপনার রেজিস্ট্রেশন আইডি সুরক্ষিত থাকবে)")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                // ১. রেজিস্ট্রেশন আইডি ব্যাকআপ রাখা
                webView.evaluateJavascript("localStorage.getItem('fcb_unique_user_id');") { regIdValue ->
                    val savedRegId = regIdValue?.replace("\"", "")

                    // ২. শুধুমাত্র ফেসবুক কুকিজ ও ব্রাউজার ক্যাশ ক্লিয়ার করা
                    webView.clearCache(true)
                    webView.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)

                    // ৩. হোম পেজে ফিরে যাওয়া এবং আইডি রিস্টোর করা
                    webView.loadUrl("file:///android_asset/index.html")

                    if (!savedRegId.isNullOrEmpty() && savedRegId != "null") {
                        webView.postDelayed({
                            webView.evaluateJavascript("localStorage.setItem('fcb_unique_user_id', '$savedRegId');", null)
                        }, 400)
                    }
                }

                Toast.makeText(this, "🧹 Facebook Cache Cleared! (ID Safe)", Toast.LENGTH_SHORT).show()
                nativeFloatingContainer.visibility = View.GONE
            }
            .setNegativeButton("না", null)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
