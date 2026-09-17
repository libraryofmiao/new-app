package `in`.miaolibrary.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gateway = "https://api.miaolibrary.in"
    private val bg = Color.rgb(248, 245, 239)
    private val ink = Color.rgb(27, 43, 58)
    private val muted = Color.rgb(105, 112, 117)
    private lateinit var content: FrameLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("session", MODE_PRIVATE)
    private fun token() = prefs().getString("access_token", null)
    private fun username() = prefs().getString("username", "Patron") ?: "Patron"
    private fun text(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
    }
    private fun shape(color: Int = Color.WHITE, radius: Int = 20) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun card(view: View) = FrameLayout(this).apply {
        background = shape()
        setPadding(dp(18), dp(16), dp(18), dp(16))
        addView(view, FrameLayout.LayoutParams(-1, -2))
    }
    private fun button(label: String, primary: Boolean = true) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) Color.WHITE else ink)
        background = shape(if (primary) ink else Color.WHITE, 16)
        stateListAnimator = null
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = bg
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (token().isNullOrBlank()) login() else dashboard("Home")
    }

    private fun login() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(bg)
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val image = ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.CENTER_INSIDE }
        box.addView(card(image), LinearLayout.LayoutParams(-1, dp(150)))
        box.addView(text("Welcome to Miao Library", 28f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1) }, LinearLayout.LayoutParams(-1, dp(70)))
        box.addView(text("Your library, wherever you are", 15f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(38)))
        val user = EditText(this).apply { hint = "Library username"; setSingleLine(); background = shape(); setPadding(dp(16), 0, dp(16), 0) }
        val pass = EditText(this).apply { hint = "Password"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; background = shape(); setPadding(dp(16), 0, dp(16), 0) }
        box.addView(user, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(12), 0, dp(10)) })
        box.addView(pass, LinearLayout.LayoutParams(-1, dp(56)))
        val signIn = button("Sign in")
        box.addView(signIn, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(20), 0, 0) })
        box.addView(text("Secure access to your library account", 12f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(42)))
        signIn.setOnClickListener {
            if (user.text.isBlank() || pass.text.isBlank()) { toast("Enter your username and password."); return@setOnClickListener }
            signIn.isEnabled = false; signIn.text = "Signing in…"
            request("/login", "POST", JSONObject().put("username", user.text.toString().trim()).put("password", pass.text.toString()), null) { ok, response ->
                runOnUiThread {
                    signIn.isEnabled = true; signIn.text = "Sign in"
                    if (!ok) { toast("Login failed."); return@runOnUiThread }
                    try {
                        val j = JSONObject(response)
                        val access = j.optString("access_token").ifBlank { j.optString("token") }
                        prefs().edit().putString("access_token", access).putString("username", user.text.toString().trim()).apply()
                        dashboard("Home")
                    } catch (_: Exception) { toast("Invalid gateway response.") }
                }
            }
        }
        root.addView(box, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun dashboard(section: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        heading.addView(text("Miao Library", 23f).apply { setTypeface(typeface, 1) })
        heading.addView(text("Welcome back, ${username()}", 13f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8)); background = shape(Color.WHITE, 24); elevation = dp(8).toFloat()
        }
        listOf("⌂\nHome", "⌕\nCatalogue", "▣\nMy Books", "●\nAccount").forEach { item ->
            val name = item.substringAfter('\n')
            val tab = TextView(this).apply {
                text = item; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(if (name == section) Color.WHITE else ink)
                background = if (name == section) shape(ink, 16) else null
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { dashboard(name) }
            }
            nav.addView(tab, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(76)))
        ViewCompatInsets.apply(root, nav)
        setContentView(root)
        showSection(section)
    }

    private object ViewCompatInsets {
        fun apply(root: View, nav: View) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, bottom)
                nav.setPadding(dp(10), dp(8), dp(10), dp(8))
                insets
            }
            root.requestApplyInsets()
        }
    }

    private fun showSection(section: String) {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(30)) }
        body.addView(text(section, 26f).apply { setTypeface(typeface, 1) })
        when (section) { "Home" -> home(body); "Catalogue" -> catalogue(body); "My Books" -> books(body); "Account" -> account(body) }
        scroll.addView(body); content.addView(scroll)
    }

    private fun home(body: LinearLayout) {
        body.addView(text("A welcoming space for learning, discovery and connection", 16f, muted).apply { setPadding(0, dp(8), 0, dp(16)) })
        body.addView(card(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text("Explore your library", 21f).apply { setTypeface(typeface, 1) }); addView(text("Find books, follow your loans and stay connected with Miao Library.", 14f, muted).apply { setPadding(0, dp(8), 0, dp(4)) }) }), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16)) })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val catalogue = button("Catalogue", false); val books = button("My Books", false)
        quick.addView(catalogue, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(6), 0) })
        quick.addView(books, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(6), 0, 0, 0) })
        catalogue.setOnClickListener { dashboard("Catalogue") }; books.setOnClickListener { dashboard("My Books") }; body.addView(quick)
        body.addView(text("Library updates", 19f).apply { setTypeface(typeface, 1); setPadding(0, dp(24), 0, dp(10)) })
        val loading = card(text("Loading announcements…", 14f, muted)); body.addView(loading)
        request("/cms/content", "GET", null, token()) { ok, response -> runOnUiThread {
            body.removeView(loading)
            if (!ok) { body.addView(card(text("Announcements are temporarily unavailable.", 14f, muted))); return@runOnUiThread }
            val items = arrayFrom(response, "items", "content", "announcements")
            if (items.length() == 0) body.addView(card(text("No current announcements.", 14f, muted))) else for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(first(item, "title", "name").ifBlank { "Announcement" }, 17f).apply { setTypeface(typeface, 1) }); addView(text(first(item, "body", "description", "html", "content"), 14f, muted).apply { setPadding(0, dp(8), 0, 0) }) }
                body.addView(card(box), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
            }
        }}
    }

    private fun catalogue(body: LinearLayout) {
        val input = EditText(this).apply { hint = "Search books, authors or subjects"; setSingleLine(); background = shape(); setPadding(dp(16), 0, dp(16), 0) }
        body.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(14), 0, dp(10)) })
        val search = button("Search catalogue"); body.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(output, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(18), 0, 0) })
        search.setOnClickListener { searchCatalogue(input.text.toString(), output, search) }; searchCatalogue("", output, search)
    }

    private fun searchCatalogue(query: String, output: LinearLayout, search: Button) {
        search.isEnabled = false; output.removeAllViews(); output.addView(text("Searching catalogue…", 14f, muted))
        request("/catalogue/search?q=" + URLEncoder.encode(query, "UTF-8"), "GET", null, token()) { ok, response -> runOnUiThread {
            search.isEnabled = true; output.removeAllViews()
            if (!ok) { output.addView(card(text("Catalogue unavailable.", 14f, muted))); return@runOnUiThread }
            val items = arrayFrom(response, "items", "results", "records", "books")
            if (items.length() == 0) output.addView(card(text("No catalogue records found.", 14f, muted))) else for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(first(item, "title").ifBlank { "Untitled" }, 18f).apply { setTypeface(typeface, 1) })
                    addIfPresent(this, "Author", item, "author")
                    addIfPresent(this, "Library", item, "library")
                    addIfPresent(this, "Availability", item, "availability")
                    addIfPresent(this, "Call number", item, "call_number")
                    addIfPresent(this, "Barcode", item, "barcode")
                    addIfPresent(this, "Holdings", item, "holding_count")
                    addIfPresent(this, "Biblionumber", item, "biblionumber")
                    addView(text("Tap for complete book details", 12f, ink).apply { setPadding(0, dp(12), 0, 0) })
                }
                val itemCard = card(box); itemCard.setOnClickListener { openDetails(item) }; output.addView(itemCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
            }
        }}
    }

    private fun addIfPresent(parent: LinearLayout, label: String, item: JSONObject, key: String) {
        val value = first(item, key)
        if (value.isNotBlank()) parent.addView(text("$label: $value", 13f, muted))
    }

    private fun openDetails(item: JSONObject) {
        val id = first(item, "biblionumber")
        if (id.isBlank()) { details(item); return }
        request("/book-details/$id", "GET", null, token()) { ok, response -> runOnUiThread {
            if (ok) try { val j = JSONObject(response); details(j.optJSONObject("book") ?: j.optJSONObject("record") ?: j) } catch (_: Exception) { details(item) } else details(item)
        }}
    }

    private fun details(item: JSONObject) {
        dashboard("Catalogue"); content.removeAllViews()
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(30)) }
        body.addView(text("Book details", 25f).apply { setTypeface(typeface, 1) })
        arrayOf("title", "subtitle", "author", "authors", "publisher", "publication", "publication_year", "year", "isbn", "isbn13", "isbn10", "call_number", "callnumber", "shelfmark", "library", "location", "availability", "status", "barcode", "holding_count", "copy_count", "holdings", "item_type", "notes", "biblionumber").forEach { field ->
            val value = first(item, field); if (value.isNotBlank()) { val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(field.replace('_', ' ').replaceFirstChar { it.uppercase() }, 12f, muted)); addView(text(value, 16f).apply { setPadding(0, dp(4), 0, 0) }) }; body.addView(card(row), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(9)) }) }
        }
        scroll.addView(body); content.addView(scroll)
    }

    private fun books(body: LinearLayout) {
        body.addView(text("Currently issued", 19f).apply { setTypeface(typeface, 1); setPadding(0, dp(14), 0, dp(10)) })
        val currentOutput = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(currentOutput)
        request("/my-books", "GET", null, token()) { ok, response -> runOnUiThread {
            if (!ok) currentOutput.addView(card(text("Unable to load your books.", 14f, muted))) else {
                val items = arrayFrom(response, "books", "items", "issues", "current", "current_books")
                if (items.length() == 0) currentOutput.addView(card(text("You have no currently issued books.", 14f, muted))) else records(items, currentOutput, true)
            }
        }}
        body.addView(text("Previous issues", 19f).apply { setTypeface(typeface, 1); setPadding(0, dp(24), 0, dp(10)) })
        val historyOutput = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(historyOutput)
        historyOutput.addView(card(text("Loading previous issues…", 14f, muted)))
        request("/issue-history", "GET", null, token()) { ok, response -> runOnUiThread {
            historyOutput.removeAllViews()
            if (!ok) historyOutput.addView(card(text("Unable to load issue history.", 14f, muted))) else {
                val items = arrayFrom(response, "items", "history", "issues", "books", "previous_issues", "previousIssues", "returned_books", "past_issues")
                if (items.length() == 0) historyOutput.addView(card(text("No previous issues found.", 14f, muted))) else records(items, historyOutput, false)
            }
        }}
    }

    private fun records(items: JSONArray, output: LinearLayout, current: Boolean) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val date = if (current) first(item, "date_due", "due_date", "date_due_formatted") else first(item, "checkout_date", "issued_date", "date_issued", "date_checkout", "date_checked_out")
            val returned = first(item, "returned_date", "checkin_date", "date_returned", "return_date", "date_checked_in")
            val secondary = first(item, "library", "location", "barcode", "call_number")
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(first(item, "title", "name").ifBlank { "Untitled" }, 18f).apply { setTypeface(typeface, 1) })
                addView(text(first(item, "author", "authors").ifBlank { "Author not available" }, 14f, muted))
                addView(text((if (current) "Due: " else "Issued: ") + date.ifBlank { "Not available" }, 14f))
                if (!current && returned.isNotBlank()) addView(text("Returned: $returned", 14f))
                if (secondary.isNotBlank()) addView(text(secondary, 13f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }
            output.addView(card(box), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
        }
    }

    private fun account(body: LinearLayout) {
        body.addView(card(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text("Library account", 20f).apply { setTypeface(typeface, 1) }); addView(text(username(), 16f, muted).apply { setPadding(0, dp(8), 0, 0) }) }), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, dp(14)) })
        val my = button("View My Books", false); body.addView(my, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) }); my.setOnClickListener { dashboard("My Books") }
        val logout = button("Sign out"); body.addView(logout, LinearLayout.LayoutParams(-1, dp(52))); logout.setOnClickListener { prefs().edit().clear().apply(); login() }
    }

    private fun request(path: String, method: String, body: JSONObject?, bearer: String?, done: (Boolean, String) -> Unit) {
        thread {
            try {
                val connection = URL(gateway + path).openConnection() as HttpURLConnection
                connection.requestMethod = method; connection.connectTimeout = 15000; connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
                if (body != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json"); connection.outputStream.use { it.write(body.toString().toByteArray()) } }
                val code = connection.responseCode; val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                done(code in 200..299, stream?.bufferedReader()?.readText() ?: "HTTP $code")
            } catch (error: Exception) { done(false, error.message ?: "Network error") }
        }
    }

    private fun first(obj: JSONObject, vararg keys: String): String {
        for (key in keys) if (obj.has(key) && !obj.isNull(key)) { val value = obj.opt(key); if (value is JSONArray || value is JSONObject) return value.toString(); if (value.toString().isNotBlank()) return value.toString() }
        return ""
    }

    private fun arrayFrom(raw: String, vararg keys: String): JSONArray {
        return try { findArray(JSONObject(raw), keys.toSet()) ?: JSONArray() } catch (_: Exception) { JSONArray() }
    }

    private fun findArray(value: Any?, wanted: Set<String>): JSONArray? {
        if (value is JSONObject) {
            for (key in wanted) value.optJSONArray(key)?.let { return it }
            val names = value.keys(); while (names.hasNext()) { val found = findArray(value.opt(names.next()), wanted); if (found != null) return found }
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) { val found = findArray(value.opt(i), wanted); if (found != null) return found }
        }
        return null
    }

    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
