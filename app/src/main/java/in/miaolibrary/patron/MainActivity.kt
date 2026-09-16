package in.miaolibrary.patron

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "miao_library_session_key"
private const val PREFS = "miao_library_secure_session"

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var loginButton: Button
    private val api = LibraryApi()
    private val session by lazy { SecureSession(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = session.readToken()
        if (token.isNullOrBlank()) showLogin() else loadHome(token)
    }

    private fun showLogin() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }
        root.addView(ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadLogo())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 230))
        root.addView(TextView(this).apply {
            text = "Miao Library"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Sign in with your library account"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }, matchWrap())
        username = EditText(this).apply { hint = "Username"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT }
        password = EditText(this).apply { hint = "Password"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        loginButton = Button(this).apply { text = "Sign in"; setOnClickListener { performLogin() } }
        status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY); setPadding(0, 20, 0, 0) }
        root.addView(username, matchWrap())
        root.addView(password, matchWrap())
        root.addView(loginButton, matchWrap())
        root.addView(status, matchWrap())
        setContentView(root)
    }

    private fun loadLogo() = assets.open("miao_logo_base64.txt").use { stream ->
        val bytes = Base64.decode(stream.bufferedReader().readText(), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun performLogin() {
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty() || pass.isEmpty()) { status.text = "Enter your username and password."; return }
        loginButton.isEnabled = false
        status.text = "Signing in…"
        Thread {
            val result = api.login(user, pass)
            runOnUiThread {
                if (result.isSuccess) {
                    session.saveToken(result.getOrThrow())
                    loadHome(result.getOrThrow())
                } else {
                    loginButton.isEnabled = true
                    status.text = result.exceptionOrNull()?.message ?: "Unable to sign in."
                }
            }
        }.start()
    }

    private fun loadHome(token: String) {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(TextView(this).apply { text = "Miao Library"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
        root.addView(TextView(this).apply { text = "My Books"; textSize = 22f; setPadding(0, 28, 0, 12) }, matchWrap())
        val content = TextView(this).apply { text = "Loading your books…"; textSize = 16f }
        root.addView(content, matchWrap())
        root.addView(Button(this).apply {
            text = "Log out"
            setOnClickListener { session.clearToken(); showLogin() }
        }, matchWrap())
        setContentView(ScrollView(this).apply { addView(root) })

        Thread {
            val result = api.myBooks(token)
            runOnUiThread {
                if (result.isSuccess) {
                    val books = result.getOrThrow()
                    content.text = if (books.isEmpty()) "You have no books currently issued." else books.joinToString("\n\n") {
                        "${it.title}\n${it.author}\nDue: ${it.dueDate.ifBlank { "Not available" }}\nCall number: ${it.callNumber.ifBlank { "Not available" }}"
                    }
                } else {
                    val error = result.exceptionOrNull()?.message.orEmpty()
                    if (error.contains("401") || error.contains("Unauthorized", true)) {
                        session.clearToken(); showLogin()
                    } else content.text = error.ifBlank { "Unable to load My Books." }
                }
            }
        }.start()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

private class SecureSession(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    fun readToken(): String? {
        val encrypted = prefs.getString("token", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { clearToken(); null }
    }

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }

    fun clearToken() { prefs.edit().clear().apply() }

    private fun getOrCreateKey(): SecretKey {
        val store = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) return (store.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}
