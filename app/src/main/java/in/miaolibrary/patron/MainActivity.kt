package `in`.miaolibrary.patron

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
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
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "miao_library_session_key"
private const val PREFS = "miao_library_secure_session"
private const val NOTIFICATION_PERMISSION_REQUEST = 1001

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val api = LibraryApi()
    private val session by lazy { SecureSession(this) }
    private var currentToken: String? = null
    private var detailsBack: (() -> Unit)? = null
    private var isHomeScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareNotificationPermission()
        val token = session.readToken()
        if (token.isNullOrBlank()) showLogin() else { currentToken = token; showHome() }
    }

    private fun prepareNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    override fun onBackPressed() {
        val action = detailsBack
        if (action != null) { detailsBack = null; action.invoke() } else super.onBackPressed()
    }

    private fun showLogin() {
        isHomeScreen = false
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 32, 48, 32); setBackgroundColor(Color.rgb(248, 250, 252)) }
        root.addView(ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(loadLogo()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 230))
        root.addView(TextView(this).apply { text = "Miao Library"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, matchWrap())
        root.addView(TextView(this).apply { text = "Sign in with your library account"; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 28) }, matchWrap())
        val username = EditText(this).apply { hint = "Username"; setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT }
        val password = EditText(this).apply { hint = "Password"; setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; transformationMethod = PasswordTransformationMethod.getInstance() }
        val toggle = Button(this).apply { text = "Show" }
        var visible = false
        toggle.setOnClickListener { visible = !visible; password.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance(); password.setSelection(password.text.length); toggle.text = if (visible) "Hide" else "Show" }
        val passwordRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(password, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        val loginButton = Button(this).apply { text = "Sign in" }
        val status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY); setPadding(0, 20, 0, 0) }
        loginButton.setOnClickListener { val user = username.text.toString().trim(); val pass = password.text.toString(); if (user.isEmpty() || pass.isEmpty()) { status.text = "Enter your username and password."; return@setOnClickListener }; loginButton.isEnabled = false; status.text = "Signing in…"; Thread { val result = api.login(user, pass); runOnUiThread { if (result.isSuccess) { val token = result.getOrThrow(); session.saveToken(token); currentToken = token; showHome() } else { loginButton.isEnabled = true; status.text = result.exceptionOrNull()?.message ?: "Unable to sign in." } } }.start() }
        root.addView(username, matchWrap()); root.addView(passwordRow, matchWrap()); root.addView(loginButton, matchWrap()); root.addView(status, matchWrap())
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun loadLogo() = assets.open("miao_logo_base64.txt").use { stream -> val bytes = Base64.decode(stream.bufferedReader().readText(), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }

    private fun showHome() {
        isHomeScreen = true
        detailsBack = null
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundColor(Color.rgb(248, 250, 252)) }
        root.addView(TextView(this).apply { text = "Miao Library"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
        root.addView(TextView(this).apply { text = "Your library account"; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, 4, 0, 8) }, matchWrap())
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 12) }
        val myBooksButton = Button(this).apply { text = "My Books" }
        val catalogueButton = Button(this).apply { text = "Catalogue" }
        navigation.addView(myBooksButton, weightWrap()); navigation.addView(catalogueButton, weightWrap()); root.addView(navigation, matchWrap())
        root.addView(Button(this).apply { text = "Account"; setOnClickListener { showAccount() } }, matchWrap())
        root.addView(Button(this).apply { text = "About & Privacy"; setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) } }, matchWrap())
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 8) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply { text = "Log out"; setOnClickListener { session.clearToken(); currentToken = null; showLogin() } }, matchWrap())
        setContentView(root)
        myBooksButton.setOnClickListener { isHomeScreen = false; loadMyBooks() }
        catalogueButton.setOnClickListener { isHomeScreen = false; showCatalogueSearch() }
        loadHomeDashboard()
    }

    private fun loadHomeDashboard() {
        content.removeAllViews()
        content.addView(sectionHeader("Home", "Library updates and your current account summary"))
        content.addView(label("Loading home…", 16f))
        val token = currentToken ?: return
        Thread {
            val booksResult = api.myBooks(token)
            val contentResult = api.libraryContent()
            runOnUiThread {
                if (!isHomeScreen) return@runOnUiThread
                content.removeAllViews()
                content.addView(sectionHeader("Home", "Library updates and your current account summary"))
                if (booksResult.isSuccess) {
                    val books = booksResult.getOrThrow()
                    content.addView(label(if (books.isEmpty()) "No books are currently issued." else "${books.size} book(s) currently issued", 16f))
                    DueDateReminderScheduler.schedule(this, books)
                    if (books.isNotEmpty()) {
                        books.take(3).forEach { book ->
                            val summary = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(label(book.title, 17f).apply { setTypeface(null, Typeface.BOLD) })
                                addView(detailRow("Due date", book.dueDate.ifBlank { "Not available" }))
                            }
                            content.addView(styledContainer(summary))
                        }
                    }
                } else {
                    content.addView(label("Unable to load your account summary.", 15f))
                    handleFailure(booksResult.exceptionOrNull())
                    if (!isHomeScreen) return@runOnUiThread
                }
                content.addView(LibraryUpdatesView.create(this, contentResult.getOrElse { emptyList() }))
            }
        }.start()
    }

    private fun showAccount() {
        isHomeScreen = false
        detailsBack = { showHome() }; content.removeAllViews(); content.addView(sectionHeader("Account", "Your registered library details")); content.addView(label("Loading account details…", 16f)); val token = currentToken ?: return
        Thread { val result = api.account(token); runOnUiThread { if (result.isSuccess) { val account = result.getOrThrow(); content.removeAllViews(); content.addView(Button(this).apply { text = "Back"; setOnClickListener { val action = detailsBack; detailsBack = null; action?.invoke() } }); content.addView(sectionHeader("Account", "Your registered library details")); val name = listOf(account.firstName, account.surname).filter { it.isNotBlank() }.joinToString(" "); val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addRowIfPresent(this, "Name", name); addRowIfPresent(this, "Username", account.username); addRowIfPresent(this, "Library card number", account.cardNumber); addRowIfPresent(this, "Email", account.email); addRowIfPresent(this, "Phone", account.phone); addRowIfPresent(this, "Address", account.address) }; content.addView(styledContainer(card)) } else handleFailure(result.exceptionOrNull()) } }.start()
    }

    private fun historyButton() = Button(this).apply { text = "View Issue History"; setOnClickListener { loadHistory() } }

    private fun loadMyBooks() {
        content.removeAllViews(); content.addView(sectionHeader("My Books", "Books currently issued to your account")); content.addView(historyButton()); content.addView(label("Loading your books…", 16f)); val token = currentToken ?: return
        Thread { val result = api.myBooks(token); runOnUiThread { if (result.isSuccess) { content.removeAllViews(); content.addView(sectionHeader("My Books", "Books currently issued to your account")); content.addView(historyButton()); val books = result.getOrThrow(); DueDateReminderScheduler.schedule(this, books); if (books.isEmpty()) content.addView(emptyState("You have no books currently issued.")) else books.forEach { book -> val id = extractBiblionumber(book.detailsUrl); val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(book.title, 18f).apply { setTypeface(null, Typeface.BOLD) }); addView(label(book.author.ifBlank { "Author not available" }, 15f)); addView(detailRow("Due date", book.dueDate.ifBlank { "Not available" })); addView(detailRow("Call number", book.callNumber.ifBlank { "Not available" })) }; content.addView(if (id != null) clickableContainer(item) { showBookDetails(id) { loadMyBooks() } } else styledContainer(item)) } } else handleFailure(result.exceptionOrNull()) } }.start()
    }

    private fun loadHistory() {
        isHomeScreen = false
        content.removeAllViews(); content.addView(sectionHeader("Issue History", "Previously issued books")); content.addView(Button(this).apply { text = "Back to My Books"; setOnClickListener { loadMyBooks() } }); content.addView(label("Loading history…", 16f)); val token = currentToken ?: return
        Thread { val result = api.issueHistory(token); runOnUiThread { if (result.isSuccess) { content.removeAllViews(); content.addView(sectionHeader("Issue History", "Previously issued books")); content.addView(Button(this).apply { text = "Back to My Books"; setOnClickListener { loadMyBooks() } }); val records = result.getOrThrow(); if (records.isEmpty()) content.addView(emptyState("No previous issues found.")) else records.forEach { record -> val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(record.title, 18f).apply { setTypeface(null, Typeface.BOLD) }); addView(label(record.author.ifBlank { "Author not available" }, 15f)); addView(detailRow("Date", record.date.ifBlank { "Not available" })); addView(detailRow("Call number", record.callNumber.ifBlank { "Not available" })); addView(detailRow("Status", record.status.ifBlank { "Not available" })) }; content.addView(styledContainer(item)) } } else handleFailure(result.exceptionOrNull()) } }.start()
    }

    private fun showCatalogueSearch() {
        isHomeScreen = false
        detailsBack = null; content.removeAllViews(); content.addView(sectionHeader("Catalogue", "Search the library collection")); val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; val query = EditText(this).apply { hint = "Search books"; setSingleLine(true) }; val searchButton = Button(this).apply { text = "Search" }; searchRow.addView(query, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); searchRow.addView(searchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)); content.addView(searchRow, matchWrap()); val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(results, matchWrap())
        searchButton.setOnClickListener { val text = query.text.toString().trim(); if (text.isEmpty()) { results.removeAllViews(); results.addView(label("Enter a search term.", 16f)); return@setOnClickListener }; searchButton.isEnabled = false; results.removeAllViews(); results.addView(label("Searching…", 16f)); val token = currentToken ?: return@setOnClickListener; Thread { val result = api.catalogueSearch(token, text); runOnUiThread { searchButton.isEnabled = true; results.removeAllViews(); if (result.isSuccess) { val items = result.getOrThrow(); if (items.isEmpty()) results.addView(emptyState("No catalogue results found.")) else items.forEach { item -> val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(item.title, 18f).apply { setTypeface(null, Typeface.BOLD) }); addView(label(item.author.ifBlank { "Author not available" }, 15f)); addView(detailRow("Library", item.library.ifBlank { "Not available" })); addView(detailRow("Call number", item.callNumber.ifBlank { "Not available" })); addView(detailRow("Availability", item.availability.ifBlank { "Not available" })); addView(detailRow("Copies", item.holdingCount.toString())) }; results.addView(clickableContainer(view) { showBookDetails(item.biblionumber) { showCatalogueSearch() } }) } } else handleFailure(result.exceptionOrNull()) } }.start() }
    }

    private fun showBookDetails(biblionumber: Int, backAction: () -> Unit = { showHome() }) {
        isHomeScreen = false
        detailsBack = backAction; content.removeAllViews(); content.addView(sectionHeader("Book Details", "Loading bibliographic and holding information…")); val token = currentToken ?: return
        Thread { val result = api.bookDetails(token, biblionumber); runOnUiThread { if (result.isSuccess) { val book = result.getOrThrow(); content.removeAllViews(); content.addView(Button(this).apply { text = "Back"; setOnClickListener { val action = detailsBack; detailsBack = null; action?.invoke() } }); content.addView(sectionHeader(book.title, book.author.ifBlank { "Author not available" })); if (book.holdings.isEmpty()) content.addView(emptyState("No holding information available.")) else book.holdings.forEachIndexed { index, h -> content.addView(label("Copy ${index + 1}", 19f).apply { setTypeface(null, Typeface.BOLD) }); val holding = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addRowIfPresent(this, "Item type", h.itemType); addRowIfPresent(this, "Current library", h.currentLibrary); addRowIfPresent(this, "Home library", h.homeLibrary); addRowIfPresent(this, "Collection", h.collection); addRowIfPresent(this, "Shelving location", h.shelvingLocation); addRowIfPresent(this, "Call number", h.callNumber); addRowIfPresent(this, "Volume", h.volumeInfo); addRowIfPresent(this, "Copy number", h.copyNumber); addRowIfPresent(this, "Status", h.status); addRowIfPresent(this, "Due date", h.dateDue); addRowIfPresent(this, "Barcode", h.barcode); addRowIfPresent(this, "Notes", h.notes) }; content.addView(styledContainer(holding)) } } else handleFailure(result.exceptionOrNull()) } }.start()
    }

    private fun addRowIfPresent(parent: LinearLayout, name: String, value: String) { if (value.isNotBlank()) parent.addView(detailRow(name, value)) }
    private fun extractBiblionumber(url: String): Int? = try { Uri.parse(url).getQueryParameter("biblionumber")?.toIntOrNull() } catch (_: Exception) { null }
    private fun handleFailure(error: Throwable?) { val message = error?.message.orEmpty(); if (message.contains("401") || message.contains("Unauthorized", true)) { session.clearToken(); currentToken = null; showLogin() } else { content.removeAllViews(); content.addView(label(message.ifBlank { "Unable to load data." }, 16f)) } }
    private fun sectionHeader(title: String, subtitle: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 18); addView(label(title, 23f).apply { setTypeface(null, Typeface.BOLD) }); addView(label(subtitle, 15f).apply { setTextColor(Color.DKGRAY) }) }
    private fun emptyState(text: String) = label(text, 16f).apply { setPadding(8, 24, 8, 24); gravity = Gravity.CENTER }
    private fun label(text: String, size: Float) = TextView(this).apply { this.text = text; textSize = size; setPadding(0, 6, 0, 6) }
    private fun detailRow(name: String, value: String) = TextView(this).apply { text = "$name: $value"; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, 3, 0, 3) }
    private fun styledContainer(child: LinearLayout) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); addView(child); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 22f; setStroke(1, Color.rgb(225, 231, 238)) }; elevation = 3f; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 14) } }
    private fun clickableContainer(child: LinearLayout, action: () -> Unit) = styledContainer(child).apply { isClickable = true; isFocusable = true; setOnClickListener { action() } }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun weightWrap() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
}

private class SecureSession(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    fun readToken(): String? { val encrypted = prefs.getString("token", null) ?: return null; val iv = prefs.getString("iv", null) ?: return null; return try { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))); String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8) } catch (_: Exception) { clearToken(); null } }
    fun saveToken(token: String) { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey()); val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8)); prefs.edit().putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply() }
    fun clearToken() { prefs.edit().clear().apply() }
    private fun getOrCreateKey(): SecretKey { val store = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }; if (store.containsAlias(KEY_ALIAS)) return (store.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey; val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE); generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return generator.generateKey() }
}
