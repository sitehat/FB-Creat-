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
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayOutputStream

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

        // --- ফেসবুক ব্রাউজিং ফাস্ট ও স্মুথ করার অপ্টিমাইজেশন সেটিংস ---
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.offscreenPreRaster = true
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
            fetchMailVerificationCode()
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

    // --- ১. প্রোফাইল টুল: শুধুমাত্র ইউজার আইডি (সংখ্যা) কপি করা ---
    private fun extractProfileId() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://m.facebook.com") ?: cookieManager.getCookie("https://facebook.com") ?: ""
        
        var cUser = ""
        for (cookie in cookies.split(";")) {
            val parts = cookie.trim().split("=")
            if (parts.size == 2 && parts[0] == "c_user") {
                cUser = parts[1]
                break
            }
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (cUser.isNotEmpty()) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Profile ID", cUser))
            Toast.makeText(this, "✅ Profile ID Copied: $cUser", Toast.LENGTH_LONG).show()
        } else {
            val url = webView.url ?: ""
            val match = "(?:id=|profile\\.php\\?id=|\\/)([0-9]{5,})".toRegex().find(url)
            if (match != null) {
                val profileId = match.groupValues[1]
                clipboard.setPrimaryClip(ClipData.newPlainText("Profile ID", profileId))
                Toast.makeText(this, "✅ Profile ID Copied: $profileId", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "⚠️ প্রথমে ফেসবুক অ্যাকাউন্টে লগইন করুন!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- ২. টু-ফ্যাক্টর টুল: শতভাগ নির্ভুল 2FA ডিকোড ও কোড জেনারেটর ---
    private fun extractAndGen2FA() {
        webView.evaluateJavascript("(function() { var text = document.body.innerText || ''; var clean = text.replace(/[\\s\\-\\_]+/g, '').toUpperCase(); var match = clean.match(/[A-Z2-7]{16,32}/); return match ? match[0] : ''; })();") { jsResult ->
            var secretKey = jsResult?.replace("\"", "")?.trim() ?: ""
            
            if (secretKey.isEmpty() || secretKey == "null") {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                    val cleanClip = clipText.replace("[^A-Z2-7]".toRegex(), "").toUpperCase()
                    val match = "[A-Z2-7]{16,32}".toRegex().find(cleanClip)
                    if (match != null) {
                        secretKey = match.value
                    }
                }
            }

            if (secretKey.isNotEmpty() && secretKey != "null") {
                process2FA(secretKey)
            } else {
                runOnUiThread {
                    showManual2FADialog()
                }
            }
        }
    }

    private fun showManual2FADialog() {
        val input = EditText(this)
        input.hint = "যেমন: 2A4LJYJVEEJ2M7UH"
        input.setPadding(40, 40, 40, 40)
        AlertDialog.Builder(this)
            .setTitle("🔑 2FA Secret Key দিন")
            .setView(input)
            .setPositiveButton("জেনারেট করুন") { _, _ ->
                val manualKey = input.text.toString().replace("\\s+".toRegex(), "").toUpperCase()
                if (manualKey.isNotEmpty()) {
                    process2FA(manualKey)
                } else {
                    Toast.makeText(this, "⚠️ সিক্রেট কী খালি রাখা যাবে না!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun process2FA(secretKey: String) {
        val code = generateTOTP(secretKey)
        if (code != null) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))
            Toast.makeText(this, "✅ সঠিক 2FA Code Copied: $code", Toast.LENGTH_LONG).show()
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
        val cleanInput = base32.replace("=", "").toUpperCase()
        var buffer = 0L
        var bitsLeft = 0
        val bos = ByteArrayOutputStream()
        
        for (char in cleanInput) {
            val valIndex = base32Chars.indexOf(char)
            if (valIndex < 0) return null
            buffer = (buffer shl 5) or valIndex.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                bos.write(((buffer shr bitsLeft) and 0xFF).toInt())
            }
        }
        return bos.toByteArray()
    }

    // --- ৩. কুকিজ টুল: চলমান সেশনের কুকিজ কপি করা ---
    private fun extractCookies() {
        val url = webView.url ?: "https://m.facebook.com"
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url) ?: ""
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        if (cookies.contains("c_user") || cookies.contains("xs")) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Cookies", cookies))
            Toast.makeText(this, "✅ Cookies Copied!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ সেশন কুকিজ পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- ৪. কোড টুল: ক্লিপবোর্ড, পেজ DOM এবং ডং ভ্যান মেইল API কানেকশন ---
    private fun fetchMailVerificationCode() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        // ১. প্রথমে ক্লিপবোর্ড চেক করা
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            val match = "\\b\\d{5,6}\\b".toRegex().find(text)
            if (match != null && text.length < 50) {
                val code = match.value
                clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", code))
                Toast.makeText(this, "✅ Code Copied from Clipboard: $code", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // ২. পেজ থেকে কোড চেক করা
        webView.evaluateJavascript("(function() { var text = document.body.innerText || ''; var m = text.match(/\\b\\d{5,6}\\b/); return m ? m[0] : ''; })();") { pageCode ->
            val codeOnPage = pageCode?.replace("\"", "")?.trim() ?: ""
            if (codeOnPage.isNotEmpty() && codeOnPage != "null") {
                clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", codeOnPage))
                Toast.makeText(this, "✅ Code Found on Page: $codeOnPage", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }

            // ৩. ডং ভ্যান মেইল API থেকে ফেচ করা
            Toast.makeText(this, "Checking Mail API...", Toast.LENGTH_SHORT).show()

            webView.evaluateJavascript("JSON.stringify({email: localStorage.getItem('fb_email') || localStorage.getItem('email') || '', refresh_token: localStorage.getItem('fb_refresh_token') || localStorage.getItem('refresh_token') || ''});") { jsonResult ->
                try {
                    Thread {
                        try {
                            val apiUrl = URL("https://tools.dongvanfb.net/api/graph_code")
                            val conn = apiUrl.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; utf-8")
                            conn.doOutput = true

                            val postData = jsonResult?.replace("\\", "") ?: "{\"email\":\"\"}"
                            val os = conn.outputStream
                            os.write(postData.toByteArray(Charsets.UTF_8))
                            os.flush()
                            os.close()

                            if (conn.responseCode == 200) {
                                val br = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                                val response = StringBuilder()
                                var line: String?
                                while (br.readLine().also { line = it } != null) {
                                    response.append(line?.trim())
                                }
                                br.close()

                                val resStr = response.toString()
                                val codeMatch = "\"code\"\\s*:\\s*\"([0-9]{5,6})\"".toRegex().find(resStr)
                                    ?: "\"code\"\\s*:\\s*([0-9]{5,6})".toRegex().find(resStr)

                                if (codeMatch != null) {
                                    val code = codeMatch.groupValues[1]
                                    runOnUiThread {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Mail Code", code))
                                        Toast.makeText(this, "✅ Mail Code Copied: $code", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    runOnUiThread {
                                        Toast.makeText(this, "⚠️ মেইলে নতুন কোনো কোড আসেনি!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                               runOnUiThread {
                                    Toast.makeText(this, "⚠️ Mail API Response Error!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "⚠️ Mail API Connection Failed!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                } catch (e: Exception) {
                    Toast.makeText(this, "⚠️ Mail Read Error!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- ৫. ক্লিয়ার ডাটা টুল: রেজিস্ট্রেশন আইডি সুরক্ষিত রেখে শুধুমাত্র ফেসবুক ক্যাশ ক্লিয়ার করা ---
    private fun clearFacebookDataOnly() {
        AlertDialog.Builder(this)
            .setTitle("Clear Facebook Cache")
            .setMessage("আপনি কি শুধুমাত্র ফেসবুকের ক্যাশ ও কুকিজ ক্লিয়ার করতে চান? (আপনার রেজিস্ট্রেশন আইডি সুরক্ষিত থাকবে)")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                webView.evaluateJavascript("localStorage.getItem('fcb_unique_user_id');") { regIdValue ->
                    val savedRegId = regIdValue?.replace("\"", "")

                    webView.clearCache(true)
                    webView.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)

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
