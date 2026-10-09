package com.fbcreat.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnFloatingPlus: CardView
    private lateinit var panelTools: CardView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        btnFloatingPlus = findViewById(R.id.btnFloatingPlus)
        panelTools = findViewById(R.id.panelTools)

        val btnMinimize: View = findViewById(R.id.btnMinimize)
        val btnProfileLink: View = findViewById(R.id.btnProfileLink)
        val btnAuthentic: View = findViewById(R.id.btnAuthentic)
        val btnCookies: View = findViewById(R.id.btnCookies)
        val btnClearData: View = findViewById(R.id.btnClearData)

        // Configure WebView Settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // JavaScript Bridge setup
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && (url.contains("facebook.com") || url.contains("fb.com"))) {
                    btnFloatingPlus.visibility = View.VISIBLE
                } else {
                    btnFloatingPlus.visibility = View.GONE
                    panelTools.visibility = View.GONE
                }
            }
        }

        // 1. Floating Plus (+) Click -> Open Panel
        btnFloatingPlus.setOnClickListener {
            btnFloatingPlus.visibility = View.GONE
            panelTools.visibility = View.VISIBLE
        }

        // 2. Minimize (-) Click -> Close Panel to Plus (+)
        btnMinimize.setOnClickListener {
            panelTools.visibility = View.GONE
            btnFloatingPlus.visibility = View.VISIBLE
        }

        // 3. Profile Link Action
        btnProfileLink.setOnClickListener {
            extractProfileLink()
        }

        // 4. Authentic Action (Extract KEY + Generate 6-digit 2FA Code)
        btnAuthentic.setOnClickListener {
            extract2FAKeyAndCode()
        }

        // 5. Cookies Action
        btnCookies.setOnClickListener {
            extractCookies()
        }

        // 6. Clear Data Action
        btnClearData.setOnClickListener {
            clearAppData()
        }

        // Load Main App Dashboard
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Function to launch Facebook Lite/Mobile Web
    fun openFbLite() {
        runOnUiThread {
            webView.loadUrl("https://m.facebook.com/")
        }
    }

    // Extract Profile UID or Profile Link
    private fun extractProfileLink() {
        val cookies = CookieManager.getInstance().getCookie(webView.url ?: "https://m.facebook.com") ?: ""
        var uid = ""
        
        val cookiePairs = cookies.split(";")
        for (pair in cookiePairs) {
            val parts = pair.trim().split("=")
            if (parts.size >= 2 && parts[0] == "c_user") {
                uid = parts[1]
                break
            }
        }

        if (uid.isNotEmpty()) {
            copyToClipboard("Profile UID", uid)
            Toast.makeText(this, "Profile UID Copied: $uid", Toast.LENGTH_SHORT).show()
        } else {
            val currentUrl = webView.url ?: ""
            copyToClipboard("Profile Link", currentUrl)
            Toast.makeText(this, "Link Copied: $currentUrl", Toast.LENGTH_SHORT).show()
        }
    }

    // Extract 2FA Secret Key and generate 6-digit TOTP Code
    private fun extract2FAKeyAndCode() {
        webView.evaluateJavascript(
            "(function() { " +
            "  var text = document.body.innerText || ''; " +
            "  var match = text.match(/([A-Z2-7]{4}\\s*){4,8}/) || text.match(/[A-Z2-7]{16,32}/); " +
            "  return match ? match[0] : ''; " +
            "})();"
        ) { value ->
            val cleanKey = value?.replace("\"", "")?.replace("\\n", "")?.replace(" ", "")?.trim() ?: ""
            if (cleanKey.isNotEmpty() && cleanKey != "null") {
                val totpCode = generateTOTP(cleanKey)
                val finalResult = if (totpCode.isNotEmpty()) "$cleanKey | $totpCode" else cleanKey
                
                copyToClipboard("2FA Key & Code", finalResult)
                
                if (totpCode.isNotEmpty()) {
                    Toast.makeText(this, "KEY & Code Copied:\nKEY: $cleanKey\nCode: $totpCode", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "KEY Copied: $cleanKey", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "2FA Key not found on current page!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper: Base32 Decoder
    private fun decodeBase32(base32: String): ByteArray {
        val cleanBase32 = base32.replace("\\s+".toRegex(), "").replace("=", "").uppercase()
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var bitCount = 0
        val bytes = ArrayList<Byte>()

        for (c in cleanBase32) {
            val valIndex = base32Chars.indexOf(c)
            if (valIndex == -1) continue
            bits = (bits shl 5) or valIndex
            bitCount += 5
            if (bitCount >= 8) {
                bytes.add(((bits shr (bitCount - 8)) and 0xFF).toByte())
                bitCount -= 8
            }
        }
        return bytes.toByteArray()
    }

    // Helper: TOTP Generator (Time-based One-Time Password)
    private fun generateTOTP(base32Key: String): String {
        return try {
            val keyBytes = decodeBase32(base32Key)
            if (keyBytes.isEmpty()) return ""
            
            val timeStep = System.currentTimeMillis() / 1000 / 30
            val data = ByteArray(8)
            var value = timeStep
            for (i in 7 downTo 0) {
                data[i] = (value and 0xFFL).toByte()
                value = value shr 8
            }

            val signKey = SecretKeySpec(keyBytes, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(signKey)
            val hash = mac.doFinal(data)

            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                         ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                         ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                         (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % 1000000
            String.format("%06d", otp)
        } catch (e: Exception) {
            ""
        }
    }

    // Extract Facebook Cookies
    private fun extractCookies() {
        val cookies = CookieManager.getInstance().getCookie(webView.url ?: "https://m.facebook.com")
        if (!cookies.isNullOrEmpty()) {
            copyToClipboard("Cookies", cookies)
            Toast.makeText(this, "Cookies Copied Successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No Cookies Found!", Toast.LENGTH_SHORT).show()
        }
    }

    // Clear All Cache, Data & Cookies
    private fun clearAppData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        
        Toast.makeText(this, "Data Cleared! Reloading FB...", Toast.LENGTH_SHORT).show()
        webView.loadUrl("https://m.facebook.com/")
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onBackPressed() {
        if (panelTools.visibility == View.VISIBLE) {
            panelTools.visibility = View.GONE
            btnFloatingPlus.visibility = View.VISIBLE
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun openFbLite() {
            this@MainActivity.openFbLite()
        }
    }
}
