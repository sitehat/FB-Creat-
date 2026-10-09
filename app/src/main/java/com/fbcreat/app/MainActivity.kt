package com.fbcreat.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnFloatingPlus: CardView
    private lateinit var panelTools: CardView

    private val FIREBASE_DATABASE_URL = "https://fb--creat-default-rtdb.firebaseio.com"

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
        val btnEmailCode: View = findViewById(R.id.btnEmailCode)

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

        btnFloatingPlus.setOnClickListener {
            btnFloatingPlus.visibility = View.GONE
            panelTools.visibility = View.VISIBLE
        }

        btnMinimize.setOnClickListener {
            panelTools.visibility = View.GONE
            btnFloatingPlus.visibility = View.VISIBLE
        }

        btnProfileLink.setOnClickListener { extractProfileLink() }
        btnAuthentic.setOnClickListener { extract2FAKeyAndCode() }
        btnCookies.setOnClickListener { extractCookies() }
        btnClearData.setOnClickListener { clearAppData() }
        btnEmailCode.setOnClickListener { extractEmailCode() }

        // ব্যাকগ্রাউন্ডে ফায়ারবেস থেকে ৩০ দিনের মেয়াদ যাচাই করা
        syncSubscriptionFromFirebase()

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun syncSubscriptionFromFirebase() {
        val regId = getOrCreateRegId()
        Thread {
            try {
                var expiryTime = checkFirebaseUrl("$FIREBASE_DATABASE_URL/$regId.json")
                if (expiryTime == 0L) {
                    expiryTime = checkFirebaseUrl("$FIREBASE_DATABASE_URL/users/$regId.json")
                }
                if (expiryTime > 0L) {
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit().putLong("expiry_time", expiryTime).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun checkFirebaseUrl(urlString: String): Long {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                if (response.isNotEmpty() && response != "null") {
                    val json = JSONObject(response)
                    json.optLong("expiry_time", 0L)
                } else 0L
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getOrCreateRegId(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var id = prefs.getString("reg_id", null)
        if (id == null) {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val shortHash = if (!androidId.isNullOrEmpty()) {
                androidId.takeLast(6).uppercase()
            } else {
                UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
            }
            id = "FBC-$shortHash"
            prefs.edit().putString("reg_id", id).apply()
        }
        return id
    }

    private fun isAccountActive(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        // বর্তমান সময় ফায়ারবেসের expiry_time এর চেয়ে কম হলে তবেই সচল থাকবে
        return System.currentTimeMillis() < expiryTime
    }

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
                Toast.makeText(this, "KEY & Code Copied:\nKEY: $cleanKey\nCode: $totpCode", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "2FA Key not found on current page!", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private fun extractCookies() {
        val cookies = CookieManager.getInstance().getCookie(webView.url ?: "https://m.facebook.com")
        if (!cookies.isNullOrEmpty()) {
            copyToClipboard("Cookies", cookies)
            Toast.makeText(this, "Cookies Copied Successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No Cookies Found!", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun extractEmailCode() {
        webView.evaluateJavascript(
            "(function() { " +
            "  var text = document.body.innerText || ''; " +
            "  var match = text.match(/FB-\\d{5}/i) || text.match(/\\b\\d{5,6}\\b/); " +
            "  return match ? match[0] : ''; " +
            "})();"
        ) { value ->
            val cleanCode = value?.replace("\"", "")?.replace("\\n", "")?.trim() ?: ""
            if (cleanCode.isNotEmpty() && cleanCode != "null") {
                copyToClipboard("Email Verification Code", cleanCode)
                Toast.makeText(this, "Verification Code Copied: $cleanCode", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Verification Code not found on page!", Toast.LENGTH_SHORT).show()
            }
        }
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
            runOnUiThread {
                webView.loadUrl("https://m.facebook.com/")
            }
        }

        @JavascriptInterface
        fun getRegId(): String {
            return this@MainActivity.getOrCreateRegId()
        }

        @JavascriptInterface
        fun isAccountActive(): Boolean {
            return this@MainActivity.isAccountActive()
        }

        @JavascriptInterface
        fun copyToClipboardJs(text: String) {
            runOnUiThread {
                this@MainActivity.copyToClipboard("Registration ID", text)
                Toast.makeText(this@MainActivity, "Registration ID Copied: $text", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
