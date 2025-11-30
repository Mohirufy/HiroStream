package com.Kuramanime

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url
                ) {
                    this.referer = url
                }
            )
        }
        else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    "$mainUrl/api/file/${mId}?download",
                ) {
                    this.referer = url
                }
            )
        }
    }
}

class KuramaDrive : ExtractorApi() {
    override val name = "KuramaDrive"
    override val mainUrl = "https://v1.kuramadrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String, 
        referer: String?, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Mohiro", "🎯 KUDRIVE FINALLY CALLED: $url")
        
        try {
            val document = app.get(url, timeout = 50L).document
            val fileId = url.substringAfter("kdrive/")
            
            // Get domain and encrypted token
            val domain = document.select("div.server-button button").attr("data-domain").toString()
            val passphrase = document.select("script")
                .mapNotNull { Regex("""window\.PASSPHRASE\s*=\s*"([^"]+)"""").find(it.data()) }
                .firstOrNull()?.groupValues?.get(1)
            
            // Get encrypted token from meta tag
            val encryptedToken = document.select("meta[name=csrf-token]").attr("content")
            
            Log.d("Mohiro", "Domain: $domain, Passphrase: $passphrase, EncryptedToken: $encryptedToken")
            
            // Decrypt token
            val bearerToken = if (!encryptedToken.isNullOrEmpty() && !passphrase.isNullOrEmpty()) {
                decryptToken(encryptedToken, passphrase) ?: run {
                    Log.d("Mohiro", "Using fallback token")
                    "gIE8DZ49juAfV8XwXwcz11hPxBWJFYrD"
                }
            } else {
                Log.d("Mohiro", "Using default token")
                "gIE8DZ49juAfV8XwXwcz11hPxBWJFYrD"
            }

            Log.d("Mohiro", "Bearer Token: $bearerToken")

            val apiUrl = "https://v1.kuramadrive.com/api/v1/drive/file/$fileId/check"
            
            val formData = mapOf(
                "domain" to domain,
                "gtokens" to ""
            )

            val response = app.post(apiUrl, data = formData, headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "authorization" to "Bearer $bearerToken"
            ))

            Log.d("Mohiro", "API Response Code: ${response.code}")

            if (!response.isSuccessful) {
                Log.d("KuramaDrive", "API call failed: ${response.code}")
                return
            }

            val jsonText = response.text
            Log.d("Mohiro", "API Response: $jsonText")
            
            val json = try {
                org.json.JSONObject(jsonText)
            } catch (e: Exception) {
                Log.d("KuramaDrive", "JSON parse failed: ${e.message}")
                return
            }

            val fileUrl = json.optJSONObject("data")?.getString("url") ?: run {
                Log.d("KuramaDrive", "No file URL in response")
                return
            }

            Log.d("Mohiro", "✅ Video URL Found: $fileUrl")

            // ✅ PAKAI newExtractorLink
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    fileUrl,
                ) {
                    this.referer = url
                }
            )
            
        } catch (e: Exception) {
            Log.d("Mohiro", "❌ Error in KuramaDrive: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun decryptToken(encryptedToken: String, passphrase: String): String? {
        return try {
            val jsonStr = android.util.Base64.decode(encryptedToken, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            val json = org.json.JSONObject(jsonStr)
            
            val iv = android.util.Base64.decode(json.getString("iv"), android.util.Base64.DEFAULT)
            val ct = android.util.Base64.decode(json.getString("ct"), android.util.Base64.DEFAULT)
            val tag = android.util.Base64.decode(json.getString("tag"), android.util.Base64.DEFAULT)
            
            // Derive key
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val key = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
            
            // Verify HMAC
            val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
            hmac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
            val hmacInput = "v1|aes-256-cbc|hmac-sha256".toByteArray() + iv + ct
            val calculatedTag = hmac.doFinal(hmacInput)
            if (!calculatedTag.contentEquals(tag)) return null
            
            // Decrypt
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, 
                       javax.crypto.spec.SecretKeySpec(key, "AES"),
                       javax.crypto.spec.IvParameterSpec(iv))
            
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.d("KuramaDrive", "Decrypt failed: ${e.message}")
            null
        }
    }
}