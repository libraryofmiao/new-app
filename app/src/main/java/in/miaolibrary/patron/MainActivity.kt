package in.miaolibrary.patron

import android.app.Activity
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private const val GATEWAY_URL = "https://api.miaolibrary.in"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "miao_library_session_key"
private const val PREFS = "miao_library_secure_session"

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SecureSession(this).hasToken()) showHome("Library member") else showLogin()
    }

    private fun showLogin() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        val logo = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadLogo())
        }
        root.addView(logo, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 230
        ))

        val title = TextView(this).apply {
            text = "Miao Library"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Sign in with your library account"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }

        username = EditText(this).apply {
            hint = "Username"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
        }
        password = EditText(this).apply {
            hint = "Password"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        loginButton = Button(this).apply {
            text = "Sign in"
            setOnClickListener { performLogin() }
        }
        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 0)
        }

        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(username, matchWrap())
        root.addView(password, matchWrap())
        root.addView(loginButton, matchWrap())
        root.addView(status, matchWrap())
        setContentView(root)
    }

    private fun loadLogo() = assets.open("miao_logo_base64.txt").use { stream ->
        val encoded = stream.bufferedReader().readText()
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun performLogin() {
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            status.text = "Enter your username and password."
            return
        }
        loginButton.isEnabled = false
        status.text = "Signing in…"
        Thread {
            val result = login(user, pass)
            runOnUiThread {
                loginButton.isEnabled = true
                if (result.first) showHome(user) else status.text = result.second
            }
        }.start()
    }

    private fun login(username: String, password: String): Pair<Boolean, String> {
        return try {
            val connection = (URL("$GATEWAY_URL/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().put("username", username).put("password", password).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrNull()
            val token = json?.optString("access_token").orEmpty()
            if (code in 200..299 && token.isNotBlank()) {
                SecureSession(this).saveToken(token)
                Pair(true, "")
            } else {
                Pair(false, json?.optString("detail").orEmpty().ifBlank {
                    "Unable to sign in. Please check your username and password."
                })
            }
        } catch (_: Exception) {
            Pair(false, "Unable to connect to the library server. Please try again.")
        }
    }

    private fun showHome(user: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Miao Library"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Welcome, $user\n\nHome\n\nMy Books\n\nCatalogue"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "Log out"
            setOnClickListener {
                SecureSession(this@MainActivity).clearToken()
                showLogin()
            }
        }, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

private class SecureSession(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    fun hasToken(): Boolean = prefs.getString("token", null) != null

    fun saveToken(token: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clearToken() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
