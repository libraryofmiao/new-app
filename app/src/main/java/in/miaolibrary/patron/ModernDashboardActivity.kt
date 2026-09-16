package `in`.miaolibrary.patron

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class ModernDashboardActivity : Activity() {
    private val api = LibraryApi()
    private val session by lazy { ModernSession(this) }
    private var token: String? = null
    private lateinit var page: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var nav: LinearLayout
    private var screen = "home"
    private var backAction: (() -> Unit)? = null

    private val green = Color.rgb(49, 92, 58)
    private val dark = Color.rgb(31, 43, 35)
    private val gold = Color.rgb(182, 122, 53)
    private val cream = Color.rgb(248, 247, 241)
    private val card = Color.WHITE
    private val muted = Color.rgb(101, 112, 105)
    private val border = Color.rgb(225, 228, 221)
    private val danger = Color.rgb(166, 65, 54)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        token = session.readToken()
        if (token.isNullOrBlank()) showLogin() else showShell("home")
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002)
        }
    }

    override fun onBackPressed() {
        backAction?.let { action -> backAction = null; action(); return }
        if (screen != "home") showShell("home") else super.onBackPressed()
    }

    private fun showLogin() {
        backAction = null
        val scroll = ScrollView(this).apply { setBackgroundColor(cream); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(28), dp(38), dp(28), dp(32)) }
        val logo = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(loadLogo()); contentDescription = "Miao Library logo" }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(190)))
        root.addView(text("MIAO LIBRARY", 13f, gold, true).apply { gravity = Gravity.CENTER; letterSpacing = .18f }, match())
        root.addView(text("Your library, in your hands", 28f, dark, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(8)) }, match())
        root.addView(text("Sign in with your library account to view books, catalogue records and library updates.", 15f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(26)) }, match())

        val username = input("Username", InputType.TYPE_CLASS_TEXT)
        val password = input("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        password.transformationMethod = PasswordTransformationMethod.getInstance()
        val passwordWrap = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        passwordWrap.addView(password, LinearLayout.LayoutParams(0, dp(58), 1f))
        passwordWrap.addView(textButton("SHOW") { password.transformationMethod = if (password.transformationMethod == null) PasswordTransformationMethod.getInstance() else HideReturnsTransformationMethod.getInstance(); password.setSelection(password.length()) }, LinearLayout.LayoutParams(dp(72), dp(52)))
        root.addView(username, matchWith(0, dp(10)))
        root.addView(passwordWrap, matchWith(0, dp(14)))

        val status = text("", 14f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }
        val signIn = primaryButton("Sign in")
        signIn.setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString()
            if (user.isBlank() || pass.isBlank()) { status.setTextColor(danger); status.text = "Enter your username and password."; return@setOnClickListener }
            signIn.isEnabled = false; signIn.alpha = .65f; status.setTextColor(muted); status.text = "Signing in…"
            Thread {
                val result = api.login(user, pass)
                runOnUiThread {
                    if (result.isSuccess) { token = result.getOrThrow(); session.saveToken(token!!); showShell("home") }
                    else { signIn.isEnabled = true; signIn.alpha = 1f; status.setTextColor(danger); status.text = friendlyError(result.exceptionOrNull()) }
                }
            }.start()
        }
        root.addView(signIn, matchWith(0, dp(8)))
        root.addView(status, match())
        root.addView(text("Secure connection · Miao Library", 12f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(28), 0, 0) }, match())
        scroll.addView(root); setContentView(scroll)
    }

    private fun showShell(target: String) {
        screen = target; backAction = null
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cream) }
        page.addView(topBar(), match())
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(24)) }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = bottomNav()
        page.addView(nav, match())
        setContentView(page)
        when (target) { "home" -> loadHome(); "catalogue" -> showCatalogue(); "books" -> loadBooks(); "account" -> loadAccount(); else -> loadHome() }
    }

    private fun topBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(12)); setBackgroundColor(Color.WHITE)
        val logo = ImageView(this@ModernDashboardActivity).apply { setImageBitmap(loadLogo()); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Miao Library" }
        addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleBox = LinearLayout(this@ModernDashboardActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titleBox.addView(text("Miao Library", 20f, dark, true), match())
        titleBox.addView(text("New Age Learning Centre", 12f, muted, false), match())
        addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        addView(textButton("ABOUT") { startActivity(Intent(this@ModernDashboardActivity, AboutActivity::class.java)) })
    }

    private fun bottomNav(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); setPadding(dp(6), dp(6), dp(6), dp(8))
        addView(navItem("HOME", "home") { showShell("home") }, weight())
        addView(navItem("CATALOGUE", "catalogue") { showShell("catalogue") }, weight())
        addView(navItem("MY BOOKS", "books") { showShell("books") }, weight())
        addView(navItem("ACCOUNT", "account") { showShell("account") }, weight())
    }

    private fun navItem(label: String, key: String, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(2, dp(5), 2, dp(5)); minimumHeight = dp(58); contentDescription = label; setOnClickListener { click() }
        val drawableId = when (key) { "home" -> R.drawable.ic_nav_home; "catalogue" -> R.drawable.ic_nav_catalogue; "books" -> R.drawable.ic_nav_books; else -> R.drawable.ic_nav_account }
        val icon = ImageView(this@ModernDashboardActivity).apply { setImageResource(drawableId); imageTintList = android.content.res.ColorStateList.valueOf(if (screen == key) green else muted); contentDescription = label; scaleType = ImageView.ScaleType.CENTER }
        val name = text(label, 10f, if (screen == key) green else muted, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) }
        addView(icon, LinearLayout.LayoutParams(-1, dp(28))); addView(name, match())
    }

    private fun loadHome() {
        body.removeAllViews()
        body.addView(text("Good to see you", 13f, gold, true), match())
        body.addView(text("Your library dashboard", 28f, dark, true).apply { setPadding(0, dp(2), 0, dp(14)) }, match())
        val tokenValue = token ?: return
        val loading = loadingCard("Loading your library…"); body.addView(loading, matchWith(0, dp(12)))
        Thread {
            val booksResult = api.myBooks(tokenValue); val contentResult = api.libraryContent(); val accountResult = api.account(tokenValue)
            runOnUiThread {
                body.removeView(loading)
                if (!booksResult.isSuccess) { showErrorCard(friendlyError(booksResult.exceptionOrNull())) { loadHome() }; return@runOnUiThread }
                val books = booksResult.getOrThrow(); DueDateReminderScheduler.schedule(this, books); val account = accountResult.getOrNull()
                body.addView(cardView().apply { addView(text("Welcome${account?.firstName?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}", 20f, dark, true), match()); addView(text("Your current library account at a glance.", 13f, muted, false).apply { setPadding(0, dp(4), 0, dp(14)) }, match()); val stats = LinearLayout(this@ModernDashboardActivity).apply { orientation = LinearLayout.HORIZONTAL; addView(stat("${books.size}", "Current books"), weight()); addView(stat("${books.count { it.dueDate.isNotBlank() }}", "Due dates"), weight()) }; addView(stats, match()) }, matchWith(0, dp(12)))
                body.addView(text("Currently Borrowed", 19f, dark, true).apply { setPadding(2, dp(6), 0, dp(8)) }, match())
                if (books.isEmpty()) body.addView(emptyCard("You have no books currently issued."), matchWith(0, dp(12))) else books.take(3).forEach { addBookCard(it, false) }
                if (books.size > 3) body.addView(textButton("VIEW ALL MY BOOKS") { showShell("books") }, matchWith(0, dp(8)))
                body.addView(text("Library Updates", 19f, dark, true).apply { setPadding(2, dp(16), 0, dp(8)) }, match()); body.addView(LibraryUpdatesView.create(this, contentResult.getOrElse { emptyList() }), match())
            }
        }.start()
    }

    private fun addBookCard(book: Book, clickable: Boolean) {
        val view = cardView().apply { addView(text(book.title.ifBlank { "Untitled" }, 18f, dark, true), match()); addView(text(book.author.ifBlank { "Author not available" }, 14f, muted, false).apply { setPadding(0, dp(4), 0, dp(12)) }, match()); addView(pill("Due ${book.dueDate.ifBlank { "date unavailable" }}"), matchWith(0, dp(6))); if (book.callNumber.isNotBlank()) addView(text("Call number  ${book.callNumber}", 12f, muted, false).apply { setPadding(0, dp(8), 0, 0) }, match()) }
        if (clickable) { val id = extractBiblionumber(book.detailsUrl); if (id != null) { view.isClickable = true; view.contentDescription = "Open details for ${book.title}"; view.setOnClickListener { showBookDetails(id) } } }
        body.addView(view, matchWith(0, dp(10)))
    }

    private fun loadBooks() {
        body.removeAllViews(); body.addView(text("My Books", 28f, dark, true), match()); body.addView(text("Books currently issued to your account", 14f, muted, false).apply { setPadding(0, dp(3), 0, dp(12)) }, match()); body.addView(textButton("ISSUE HISTORY") { showHistory() }, matchWith(0, dp(6)))
        val loading = loadingCard("Loading your books…"); body.addView(loading, matchWith(0, dp(10))); val tokenValue = token ?: return
        Thread { val result = api.myBooks(tokenValue); runOnUiThread { body.removeView(loading); if (result.isSuccess) { val books = result.getOrThrow(); DueDateReminderScheduler.schedule(this, books); if (books.isEmpty()) body.addView(emptyCard("No books are currently issued."), match()) else books.forEach { addBookCard(it, true) } } else showErrorCard(friendlyError(result.exceptionOrNull())) { loadBooks() } } }.start()
    }

    private fun showHistory() {
        backAction = { showShell("books") }; body.removeAllViews(); body.addView(backButton { showShell("books") }, match()); body.addView(text("Issue History", 28f, dark, true), match()); body.addView(text("Previously issued books", 14f, muted, false).apply { setPadding(0, dp(3), 0, dp(12)) }, match()); val tokenValue = token ?: return
        val loading = loadingCard("Loading history…"); body.addView(loading, match())
        Thread { val result = api.issueHistory(tokenValue); runOnUiThread { body.removeView(loading); if (result.isSuccess) { val records = result.getOrThrow(); if (records.isEmpty()) body.addView(emptyCard("No previous issues found."), match()) else records.forEach { record -> body.addView(cardView().apply { addView(text(record.title, 18f, dark, true), match()); addView(text(record.author.ifBlank { "Author not available" }, 14f, muted, false).apply { setPadding(0, dp(4), 0, dp(8)) }, match()); addDetail(this, "Date", record.date); addDetail(this, "Call number", record.callNumber); addDetail(this, "Status", record.status) }, matchWith(0, dp(10))) } } else showErrorCard(friendlyError(result.exceptionOrNull())) { showHistory() } } }.start()
    }

    private fun showCatalogue() {
        body.removeAllViews(); body.addView(text("Catalogue", 28f, dark, true), match()); body.addView(text("Explore the Miao Library collection", 14f, muted, false).apply { setPadding(0, dp(3), 0, dp(12)) }, match())
        val search = EditText(this).apply { hint = "Search title, author or keyword"; contentDescription = "Catalogue search"; setSingleLine(true); textSize = 15f; minHeight = dp(58); setPadding(dp(16), 0, dp(12), 0); background = rounded(Color.WHITE, border, 16) }
        val button = primaryButton("SEARCH"); body.addView(search, matchWith(0, dp(8))); body.addView(button, matchWith(0, dp(10)))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(results, match())
        button.setOnClickListener { val query = search.text.toString().trim(); if (query.isBlank()) { results.removeAllViews(); results.addView(errorCard("Enter a search term.", null), match()); return@setOnClickListener }; button.isEnabled = false; button.alpha = .65f; results.removeAllViews(); results.addView(loadingCard("Searching the catalogue…"), match()); val tokenValue = token ?: return@setOnClickListener; Thread { val result = api.catalogueSearch(tokenValue, query); runOnUiThread { button.isEnabled = true; button.alpha = 1f; results.removeAllViews(); if (result.isSuccess) { val items = result.getOrThrow(); if (items.isEmpty()) results.addView(emptyCard("No catalogue results found."), match()) else items.forEach { item -> val resultCard = cardView().apply { addView(text(item.title.ifBlank { "Untitled" }, 18f, dark, true), match()); addView(text(item.author.ifBlank { "Author not available" }, 14f, muted, false).apply { setPadding(0, dp(4), 0, dp(8)) }, match()); addDetail(this, "Library", item.library); addDetail(this, "Call number", item.callNumber); addDetail(this, "Availability", item.availability); addDetail(this, "Copies", item.holdingCount.toString()) }; resultCard.isClickable = true; resultCard.contentDescription = "Open catalogue details for ${item.title}"; resultCard.setOnClickListener { showBookDetails(item.biblionumber) }; results.addView(resultCard, matchWith(0, dp(10))) } } else results.addView(errorCard(friendlyError(result.exceptionOrNull())) { button.performClick() }, match()) } } }
    }

    private fun showBookDetails(id: Int) {
        val previous = screen; backAction = { showShell(previous) }; body.removeAllViews(); body.addView(backButton { showShell(previous) }, match()); body.addView(text("Book Details", 14f, gold, true), match()); body.addView(text("Loading…", 27f, dark, true), match()); val tokenValue = token ?: return
        Thread { val result = api.bookDetails(tokenValue, id); runOnUiThread { if (result.isSuccess) { val book = result.getOrThrow(); body.removeAllViews(); body.addView(backButton { showShell(previous) }, match()); body.addView(text(book.title.ifBlank { "Untitled" }, 27f, dark, true), match()); body.addView(text(book.author.ifBlank { "Author not available" }, 15f, muted, false).apply { setPadding(0, dp(4), 0, dp(16)) }, match()); if (book.holdings.isEmpty()) body.addView(emptyCard("No holding information available."), match()) else book.holdings.forEachIndexed { index, holding -> body.addView(text("Copy ${index + 1}", 18f, dark, true).apply { setPadding(dp(2), dp(8), 0, dp(8)) }, match()); body.addView(cardView().apply { addDetail(this, "Item type", holding.itemType); addDetail(this, "Current library", holding.currentLibrary); addDetail(this, "Home library", holding.homeLibrary); addDetail(this, "Collection", holding.collection); addDetail(this, "Shelving location", holding.shelvingLocation); addDetail(this, "Call number", holding.callNumber); addDetail(this, "Volume", holding.volumeInfo); addDetail(this, "Copy number", holding.copyNumber); addDetail(this, "Status", holding.status); addDetail(this, "Due date", holding.dateDue); addDetail(this, "Barcode", holding.barcode); addDetail(this, "Notes", holding.notes) }, matchWith(0, dp(10))) } } else showErrorCard(friendlyError(result.exceptionOrNull())) { showBookDetails(id) } } }.start()
    }

    private fun loadAccount() {
        body.removeAllViews(); body.addView(text("Account", 28f, dark, true), match()); body.addView(text("Your registered library details", 14f, muted, false).apply { setPadding(0, dp(3), 0, dp(12)) }, match()); val tokenValue = token ?: return; val loading = loadingCard("Loading account…"); body.addView(loading, match())
        Thread { val result = api.account(tokenValue); runOnUiThread { body.removeView(loading); if (result.isSuccess) { val a = result.getOrThrow(); body.addView(cardView().apply { addDetail(this, "Name", listOf(a.firstName, a.surname).filter { it.isNotBlank() }.joinToString(" ")); addDetail(this, "Username", a.username); addDetail(this, "Library card number", a.cardNumber); addDetail(this, "Email", a.email); addDetail(this, "Phone", a.phone); addDetail(this, "Address", a.address) }, matchWith(0, dp(14))); body.addView(textButton("ABOUT & PRIVACY") { startActivity(Intent(this@ModernDashboardActivity, AboutActivity::class.java)) }, matchWith(0, dp(8))); body.addView(textButton("SIGN OUT") { session.clear(); token = null; showLogin() }, match()) } else showErrorCard(friendlyError(result.exceptionOrNull())) { loadAccount() } } }.start()
    }

    private fun loadingCard(message: String): LinearLayout = cardView().apply { gravity = Gravity.CENTER_VERTICAL; addView(text("⏳  $message", 15f, muted, false), match()) }

    private fun showErrorCard(message: String, retry: (() -> Unit)?) { body.addView(errorCard(message, retry), matchWith(0, dp(12))) }

    private fun errorCard(message: String, retry: (() -> Unit)?): LinearLayout = cardView().apply { addView(text("Something went wrong", 17f, danger, true), match()); addView(text(message, 14f, muted, false).apply { setPadding(0, dp(6), 0, dp(10)) }, match()); retry?.let { addView(textButton("TRY AGAIN", it), match()) } }

    private fun friendlyError(error: Throwable?): String { val raw = error?.message.orEmpty(); if (!isOnline()) return "No internet connection. Check your connection and try again."; if (raw.contains("401") || raw.contains("Unauthorized", true)) return "Your session has expired. Please sign in again."; return raw.ifBlank { "Unable to load this information. Please try again." } }

    private fun isOnline(): Boolean { val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager; val network = manager.activeNetwork ?: return false; val caps = manager.getNetworkCapabilities(network) ?: return false; return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }

    private fun addDetail(parent: LinearLayout, name: String, value: String) { if (value.isBlank()) return; val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }; row.addView(text(name, 112f, muted, true), LinearLayout.LayoutParams(dp(112), -2)); row.addView(text(value, 14f, dark, false), LinearLayout.LayoutParams(0, -2, 1f)); parent.addView(row) }

    private fun stat(value: String, label: String): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(Color.rgb(244, 247, 241), Color.TRANSPARENT, 14); addView(text(value, 24f, green, true), match()); addView(text(label, 11f, muted, false), match()) }
    private fun pill(value: String): TextView = text(value, 12f, green, true).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(7), dp(12), dp(7)); background = rounded(Color.rgb(235, 242, 235), Color.TRANSPARENT, 18) }
    private fun cardView(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); background = rounded(card, border, 18); elevation = dp(1).toFloat() }
    private fun emptyCard(message: String): LinearLayout = cardView().apply { gravity = Gravity.CENTER; addView(text("◎", 30f, gold, true).apply { gravity = Gravity.CENTER }, match()); addView(text(message, 15f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }, match()) }
    private fun backButton(action: () -> Unit): TextView = textButton("‹  BACK", action)
    private fun primaryButton(label: String): TextView = text(label, 14f, Color.WHITE, true).apply { gravity = Gravity.CENTER; minimumHeight = dp(56); setPadding(dp(18), dp(14), dp(18), dp(14)); background = rounded(green, Color.TRANSPARENT, 16); isClickable = true }
    private fun textButton(label: String, action: () -> Unit): TextView = text(label, 12f, green, true).apply { gravity = Gravity.CENTER; minimumHeight = dp(48); setPadding(dp(10), dp(10), dp(10), dp(10)); isClickable = true; setOnClickListener { action() } }
    private fun input(hintText: String, type: Int): EditText = EditText(this).apply { hint = hintText; contentDescription = hintText; textSize = 15f; inputType = type; setSingleLine(true); minHeight = dp(58); setPadding(dp(16), 0, dp(16), 0); background = rounded(Color.WHITE, border, 16) }
    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(color); typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; includeFontPadding = false }
    private fun rounded(fill: Int, stroke: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(fill); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun matchWith(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top; bottomMargin = bottom }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun extractBiblionumber(url: String): Int? = runCatching { Uri.parse(url).getQueryParameter("biblionumber")?.toIntOrNull() }.getOrNull()
    private fun loadLogo() = runCatching { assets.open("miao_logo_base64.txt").use { val bytes = android.util.Base64.decode(it.bufferedReader().readText(), android.util.Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } }.getOrNull()
}