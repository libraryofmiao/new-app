package in.miaolibrary.patron

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
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
    private lateinit var content: LinearLayout
    private val api = LibraryApi()
    private val session by lazy { SecureSession(this) }
    private var currentToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = session.readToken()
        if (token.isNullOrBlank()) showLogin() else { currentToken = token; showHome() }
    }

    private fun showLogin() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 32, 48, 32) }
        root.addView(ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadLogo())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 230))
        root.addView(TextView(this).apply { text = "Miao Library"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, matchWrap())
        root.addView(TextView(this).apply { text = "Sign in with your library account"; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 28) }, matchWrap())
        val username = EditText(this).apply { hint = "Username"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT }
        val password = EditText(this).apply { hint = "Password"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val loginButton = Button(this).apply { text = "Sign in" }
        val status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY); setPadding(0, 20, 0, 0) }
        loginButton.setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString()
            if (user.isEmpty() || pass.isEmpty()) { status.text = "Enter your username and password."; return@setOnClickListener }
            loginButton.isEnabled = false
            status.text = "Signing in…"
            Thread {
                val result = api.login(user, pass)
                runOnUiThread {
                    if (result.isSuccess) {
                        val token = result.getOrThrow()
                        session.saveToken(token)
                        currentToken = token
                        showHome()
                    } else {
                        loginButton.isEnabled = true
                        status.text = result.exceptionOrNull()?.message ?: "Unable to sign in."
                    }
                }
            }.start()
        }
        root.addView(username, matchWrap()); root.addView(password, matchWrap()); root.addView(loginButton, matchWrap()); root.addView(status, matchWrap())
        setContentView(root)
    }

    private fun loadLogo() = assets.open("miao_logo_base64.txt").use { stream ->
        val bytes = Base64.decode(stream.bufferedReader().readText(), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun showHome() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Miao Library"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 18, 0, 12) }
        val myBooksButton = Button(this).apply { text = "My Books" }
        val historyButton = Button(this).apply { text = "History" }
        val catalogueButton = Button(this).apply { text = "Catalogue" }
        navigation.addView(myBooksButton, weightWrap()); navigation.addView(historyButton, weightWrap()); navigation.addView(catalogueButton, weightWrap())
        root.addView(navigation, matchWrap())
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply { text = "Log out"; setOnClickListener { session.clearToken(); currentToken = null; showLogin() } }, matchWrap())
        setContentView(root)
        myBooksButton.setOnClickListener { loadMyBooks() }
        historyButton.setOnClickListener { loadHistory() }
        catalogueButton.setOnClickListener { showCatalogueSearch() }
        loadMyBooks()
    }

    private fun loadMyBooks() {
        content.removeAllViews(); content.addView(label("My Books", 22f)); content.addView(label("Loading your books…", 16f))
        val token = currentToken ?: return
        Thread {
            val result = api.myBooks(token)
            runOnUiThread {
                if (result.isSuccess) {
                    content.removeAllViews(); content.addView(label("My Books", 22f))
                    val books = result.getOrThrow()
                    if (books.isEmpty()) content.addView(label("You have no books currently issued.", 16f))
                    else books.forEach { book ->
                        content.addView(card("${book.title}\n${book.author.ifBlank { "Author not available" }}\nDue: ${book.dueDate.ifBlank { "Not available" }}\nCall number: ${book.callNumber.ifBlank { "Not available" }}"))
                    }
                } else handleFailure(result.exceptionOrNull())
            }
        }.start()
    }

    private fun loadHistory() {
        content.removeAllViews(); content.addView(label("Issue History", 22f)); content.addView(label("Loading history…", 16f))
        val token = currentToken ?: return
        Thread {
            val result = api.issueHistory(token)
            runOnUiThread {
                if (result.isSuccess) {
                    content.removeAllViews(); content.addView(label("Issue History", 22f))
                    val records = result.getOrThrow()
                    if (records.isEmpty()) content.addView(label("No previous issues found.", 16f))
                    else records.forEach { record -> content.addView(card("${record.title}\n${record.author.ifBlank { "Author not available" }}\nDate: ${record.date.ifBlank { "Not available" }}\nCall number: ${record.callNumber.ifBlank { "Not available" }}")) }
                } else handleFailure(result.exceptionOrNull())
            }
        }.start()
    }

    private fun showCatalogueSearch() {
        content.removeAllViews(); content.addView(label("Catalogue", 22f))
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val query = EditText(this).apply { hint = "Search books"; singleLine = true }
        val searchButton = Button(this).apply { text = "Search" }
        searchRow.addView(query, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); searchRow.addView(searchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(searchRow, matchWrap())
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results, matchWrap())
        searchButton.setOnClickListener {
            val text = query.text.toString().trim()
            if (text.isEmpty()) { results.removeAllViews(); results.addView(label("Enter a search term.", 16f)); return@setOnClickListener }
            searchButton.isEnabled = false; results.removeAllViews(); results.addView(label("Searching…", 16f))
            val token = currentToken ?: return@setOnClickListener
            Thread {
                val result = api.catalogueSearch(token, text)
                runOnUiThread {
                    searchButton.isEnabled = true; results.removeAllViews()
                    if (result.isSuccess) {
                        val items = result.getOrThrow()
                        if (items.isEmpty()) results.addView(label("No catalogue results found.", 16f))
                        else items.forEach { item -> results.addView(card("${item.title}\n${item.author.ifBlank { "Author not available" }}\nLibrary: ${item.library.ifBlank { "Not available" }}\nCall number: ${item.callNumber.ifBlank { "Not available" }}\nAvailability: ${item.availability.ifBlank { "Not available" }}\nCopies: ${item.holdingCount}")) }
                    } else handleFailure(result.exceptionOrNull())
                }
            }.start()
        }
    }

    private fun handleFailure(error: Throwable?) {
        val message = error?.message.orEmpty()
        if (message.contains("401") || message.contains("Unauthorized", true)) { session.clearToken(); currentToken = null; showLogin() }
        else { content.removeAllViews(); content.addView(label(message.ifBlank { "Unable to load data." }, 16f)) }
    }

    private fun label(text: String, size: Float) = TextView(this).apply { this.text = text; textSize = size; setPadding(0, 10, 0, 10) }
    private fun card(text: String) = TextView(this).apply { this.text = text; textSize = 16f; setPadding(16, 18, 16, 18); setBackgroundColor(Color.rgb(245, 245, 245)); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 12) } }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun weightWrap() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
