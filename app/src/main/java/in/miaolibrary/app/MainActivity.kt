package `in`.miaolibrary.app

import android.graphics.Color
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
    private val bg = Color.rgb(247, 243, 236)
    private val navy = Color.rgb(28, 45, 63)
    private val blue = Color.rgb(45, 83, 111)
    private lateinit var content: FrameLayout

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 16f, color: Int = navy): TextView {
        return TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun prefs() = getSharedPreferences("session", MODE_PRIVATE)
    private fun token() = prefs().getString("access_token", null)
    private fun username() = prefs().getString("username", "Patron") ?: "Patron"

    private fun logo(height: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(-1, dp(height))
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (token().isNullOrBlank()) showLogin() else showDashboard("Home")
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(20), dp(28), dp(28))
            setBackgroundColor(bg)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(logo(130))
        box.addView(text("Miao Library", 30f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, 1)
        }, LinearLayout.LayoutParams(-1, dp(55)))
        box.addView(text("Sign in to access your library account", 15f, Color.DKGRAY).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(45)))
        val user = EditText(this).apply {
            hint = "Library username"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val pass = EditText(this).apply {
            hint = "Password"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(user, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(10), 0, 0) })
        box.addView(pass, LinearLayout.LayoutParams(-1, dp(56)))
        val button = Button(this).apply {
            text = "Sign in"
            isAllCaps = false
        }
        box.addView(button, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(20), 0, 0) })
        button.setOnClickListener {
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (u.isBlank() || p.isBlank()) {
                toast("Enter your username and password.")
                return@setOnClickListener
            }
            button.isEnabled = false
            button.text = "Signing in…"
            request("/login", "POST", JSONObject().put("username", u).put("password", p), null) { ok, body ->
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "Sign in"
                    if (!ok) {
                        toast("Login failed. Please check your credentials.")
                        return@runOnUiThread
                    }
                    try {
                        val result = JSONObject(body)
                        val access = result.optString("access_token").ifBlank { result.optString("token") }
                        if (access.isBlank()) throw Exception("Missing token")
                        prefs().edit().putString("access_token", access).putString("username", u).apply()
                        showDashboard("Home")
                    } catch (_: Exception) {
                        toast("Invalid gateway response.")
                    }
                }
            }
        }
        root.addView(box, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun showDashboard(section: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        heading.addView(text("Miao Library", 25f).apply { setTypeface(typeface, 1) })
        heading.addView(text("Welcome back, ${username()}", 14f, Color.DKGRAY))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
            setPadding(0, 0, 0, dp(4))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        val names = listOf("Home", "Catalogue", "My Books", "Account")
        for (name in names) {
            val item = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(blue)
                isClickable = true
                isFocusable = true
                setOnClickListener { showDashboard(name) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(62)))
        setContentView(root)
        root.requestApplyInsets()
        showSection(section)
    }

    private fun showSection(section: String) {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(40))
        }
        body.addView(text(section, 22f).apply { setTypeface(typeface, 1) })
        when (section) {
            "Home" -> loadHome(body)
            "Catalogue" -> showCatalogue(body)
            "My Books" -> loadBooks(body)
            "Account" -> showAccount(body)
        }
        scroll.addView(body)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadHome(body: LinearLayout) {
        body.addView(logo(100))
        body.addView(text("Library announcements and updates", 14f, Color.DKGRAY))
        val loading = text("Loading announcements…")
        loading.setPadding(dp(18), dp(18), dp(18), dp(18))
        loading.setBackgroundColor(Color.WHITE)
        body.addView(loading, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })
        request("/cms/content", "GET", null, token()) { ok, response ->
            runOnUiThread {
                if (!ok) {
                    loading.text = "Announcements are temporarily unavailable."
                    return@runOnUiThread
                }
                try {
                    val records = arrayFrom(response, "items", "content", "announcements")
                    body.removeView(loading)
                    if (records.length() == 0) {
                        body.addView(text("No current announcements."))
                    } else {
                        for (i in 0 until records.length()) {
                            val record = records.optJSONObject(i) ?: continue
                            val title = first(record, "title", "name").ifBlank { "Announcement" }
                            val message = first(record, "body", "description", "html", "content")
                            val card = text("$title\n$message", 15f)
                            card.setPadding(dp(18), dp(16), dp(18), dp(16))
                            card.setBackgroundColor(Color.WHITE)
                            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
                        }
                    }
                } catch (_: Exception) {
                    loading.text = "Unable to display announcements."
                }
            }
        }
    }

    private fun showCatalogue(body: LinearLayout) {
        val input = EditText(this).apply {
            hint = "Search books"
            setSingleLine()
        }
        body.addView(input, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(14), 0, dp(10)) })
        val button = Button(this).apply {
            text = "Search catalogue"
            isAllCaps = false
        }
        body.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })
        button.setOnClickListener { searchCatalogue(input.text.toString().trim(), results, button) }
        searchCatalogue("", results, button)
    }

    private fun searchCatalogue(query: String, results: LinearLayout, button: Button) {
        button.isEnabled = false
        results.removeAllViews()
        results.addView(text("Loading catalogue…"))
        val path = "/catalogue/search?q=" + URLEncoder.encode(query, "UTF-8")
        request(path, "GET", null, token()) { ok, response ->
            runOnUiThread {
                button.isEnabled = true
                results.removeAllViews()
                if (!ok) {
                    results.addView(text("Catalogue is temporarily unavailable."))
                    return@runOnUiThread
                }
                try {
                    val records = arrayFrom(response, "items", "results", "records", "books")
                    if (records.length() == 0) {
                        results.addView(text("No catalogue records found."))
                    } else {
                        for (i in 0 until records.length()) {
                            val record = records.optJSONObject(i) ?: continue
                            val card = text(
                                first(record, "title", "name").ifBlank { "Untitled" } + "\n" +
                                    first(record, "author", "authors", "creator") + "\n" +
                                    first(record, "library", "location"), 15f
                            )
                            card.setPadding(dp(16), dp(14), dp(16), dp(14))
                            card.setBackgroundColor(Color.WHITE)
                            card.isClickable = true
                            card.setOnClickListener { openBook(record) }
                            results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
                        }
                    }
                } catch (_: Exception) {
                    results.addView(text("Unable to read catalogue response."))
                }
            }
        }
    }

    private fun openBook(record: JSONObject) {
        val id = first(record, "biblionumber", "biblio_id", "id")
        if (id.isBlank()) {
            showBookDetails(record)
            return
        }
        request("/book-details/$id", "GET", null, token()) { ok, response ->
            runOnUiThread {
                if (ok) {
                    try {
                        val result = JSONObject(response)
                        val detail = result.optJSONObject("book") ?: result.optJSONObject("record") ?: result
                        showBookDetails(detail)
                    } catch (_: Exception) {
                        showBookDetails(record)
                    }
                } else {
                    showBookDetails(record)
                }
            }
        }
    }

    private fun showBookDetails(record: JSONObject) {
        showDashboard("Catalogue")
        content.removeAllViews()
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(36))
        }
        body.addView(text("Book details", 22f).apply { setTypeface(typeface, 1) })
        val fields = arrayOf("title", "subtitle", "author", "authors", "publisher", "publication", "publication_year", "year", "isbn", "call_number", "library", "location", "availability", "status", "barcode", "holding_count", "copy_count", "holdings", "item_type", "notes", "biblionumber")
        for (field in fields) {
            val value = first(record, field)
            if (value.isNotBlank()) {
                val row = text(field.replace('_', ' ').replaceFirstChar { it.uppercase() } + "\n" + value, 15f)
                row.setPadding(dp(16), dp(12), dp(16), dp(12))
                row.setBackgroundColor(Color.WHITE)
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })
            }
        }
        scroll.addView(body)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadBooks(body: LinearLayout) {
        val history = Button(this).apply {
            text = "Issue History / Previous Issues"
            isAllCaps = false
        }
        body.addView(history, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(14), 0, dp(12)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        history.setOnClickListener { loadHistoryInto(list) }
        request("/my-books", "GET", null, token()) { ok, response ->
            runOnUiThread {
                if (!ok) {
                    list.addView(text("Unable to load your books."))
                    return@runOnUiThread
                }
                try {
                    val records = arrayFrom(response, "books", "items", "issues")
                    if (records.length() == 0) list.addView(text("You have no currently issued books.")) else renderRecords(records, list, true)
                } catch (_: Exception) {
                    list.addView(text("Unable to read your books."))
                }
            }
        }
    }

    private fun loadHistoryInto(list: LinearLayout) {
        list.removeAllViews()
        list.addView(text("Loading issue history…"))
        request("/issue-history", "GET", null, token()) { ok, response ->
            runOnUiThread {
                list.removeAllViews()
                if (!ok) {
                    list.addView(text("Unable to load issue history."))
                    return@runOnUiThread
                }
                try {
                    val records = arrayFrom(response, "items", "history", "issues", "books")
                    if (records.length() == 0) list.addView(text("No previous issues found.")) else renderRecords(records, list, false)
                } catch (_: Exception) {
                    list.addView(text("Unable to read issue history."))
                }
            }
        }
    }

    private fun renderRecords(records: JSONArray, list: LinearLayout, current: Boolean) {
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i) ?: continue
            val date = if (current) first(record, "date_due", "due_date") else first(record, "checkout_date", "issued_date", "date_issued")
            val extra = first(record, "returned_date", "checkin_date", "date_returned", "library", "barcode")
            val card = text(
                first(record, "title", "name").ifBlank { "Untitled" } + "\n" +
                    first(record, "author", "authors") + "\n" +
                    (if (current) "Due: " else "Issued: ") + date.ifBlank { "Not available" } + "\n" + extra, 15f
            )
            card.setPadding(dp(16), dp(16), dp(16), dp(16))
            card.setBackgroundColor(Color.WHITE)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
        }
    }

    private fun showAccount(body: LinearLayout) {
        body.addView(text("Signed in as ${username()}", 16f), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, dp(20)) })
        val history = Button(this).apply {
            text = "Open Issue History"
            isAllCaps = false
        }
        body.addView(history, LinearLayout.LayoutParams(-1, dp(50)))
        history.setOnClickListener { showDashboard("My Books") }
        val logout = Button(this).apply {
            text = "Log out"
            isAllCaps = false
        }
        body.addView(logout, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(12), 0, 0) })
        logout.setOnClickListener {
            prefs().edit().clear().apply()
            showLogin()
        }
    }

    private fun first(record: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = record.opt(key)
            if (value != null && !value.toString().equals("null", true) && value.toString().isNotBlank()) {
                return if (value is JSONArray) value.join(", ") else value.toString()
            }
        }
        return ""
    }

    private fun arrayFrom(raw: String, vararg keys: String): JSONArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val json = JSONObject(trimmed)
        for (key in keys) {
            val array = json.optJSONArray(key)
            if (array != null) return array
            val nested = json.optJSONObject(key)
            if (nested != null) {
                val result = arrayFrom(nested.toString(), *keys)
                if (result.length() > 0) return result
            }
        }
        return JSONArray()
    }

    private fun request(path: String, method: String, payload: JSONObject?, auth: String?, callback: (Boolean, String) -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(gateway + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Accept", "application/json")
                    if (auth != null) setRequestProperty("Authorization", "Bearer $auth")
                    if (payload != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
                if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                callback(code in 200..299, response)
            } catch (error: Exception) {
                callback(false, error.message.orEmpty())
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
