package `in`.miaolibrary.app

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gateway = "https://api.miaolibrary.in"
    private val bg = Color.rgb(247, 243, 236)
    private val navy = Color.rgb(28, 45, 63)
    private val blue = Color.rgb(45, 83, 111)
    private lateinit var content: FrameLayout
    private lateinit var loading: ProgressBar
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f, color: Int = navy) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (sessionToken().isNullOrBlank()) showLogin() else showDashboard("Home")
    }

    private fun sessionToken() = getSharedPreferences("session", MODE_PRIVATE).getString("access_token", null)
    private fun username() = getSharedPreferences("session", MODE_PRIVATE).getString("username", "Patron") ?: "Patron"

    private fun showLogin() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(20), dp(28), dp(28)); setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(20), dp(4), dp(20)) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Miao Library logo" }
        box.addView(logo, LinearLayout.LayoutParams(-1, dp(180)))
        box.addView(text("Welcome to Miao Library", 25f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1) }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(6)) })
        box.addView(text("Sign in to access your library account", 14f, Color.DKGRAY).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(24)) })
        val userLayout = TextInputLayout(this).apply { hint = "Library username" }
        val user = TextInputEditText(this).apply { setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; imeOptions = 5 }
        userLayout.addView(user); box.addView(userLayout, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
        val passLayout = TextInputLayout(this).apply { hint = "Password"; endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
        val pass = TextInputEditText(this).apply { setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; imeOptions = 6 }
        passLayout.addView(pass); box.addView(passLayout, LinearLayout.LayoutParams(-1, -2))
        val button = MaterialButton(this).apply { text = "Sign in"; isAllCaps = false; minHeight = 0; minimumHeight = 0 }
        box.addView(button, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(22), 0, 0) })
        button.setOnClickListener {
            val u = user.text?.toString()?.trim().orEmpty(); val p = pass.text?.toString().orEmpty()
            if (u.isBlank() || p.isBlank()) { toast("Enter your username and password."); return@setOnClickListener }
            button.isEnabled = false; button.text = "Signing in…"
            request("/login", "POST", JSONObject().put("username", u).put("password", p), null) { ok, body ->
                runOnUiThread {
                    button.isEnabled = true; button.text = "Sign in"
                    if (!ok) toast("Login failed. Please check your credentials.") else try {
                        val j = JSONObject(body); val token = j.optString("access_token").ifBlank { j.optString("token") }
                        if (token.isBlank()) throw Exception()
                        getSharedPreferences("session", MODE_PRIVATE).edit().putString("access_token", token).putString("username", u).apply()
                        showDashboard("Home")
                    } catch (_: Exception) { toast("Invalid gateway response.") }
                }
            }
        }
        scroll.addView(box); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }

    private fun showDashboard(section: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(10)) }
        top.addView(text("Miao Library", 27f).apply { setTypeface(typeface, 1) })
        top.addView(text("Welcome back, ${username()}", 14f, Color.DKGRAY), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, 0) })
        root.addView(top)
        content = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); elevation = dp(8).toFloat(); isClickable = true }
        listOf("Home", "Catalogue", "My Books", "Account").forEach { label ->
            val item = TextView(this).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(blue); setPadding(dp(2), dp(13), dp(2), dp(13)); isClickable = true; isFocusable = true; setOnClickListener { showDashboard(label) } }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(62)))
        setContentView(root)
        showSection(section)
    }

    private fun showSection(section: String) {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; isClickable = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(10), dp(22), dp(24)) }
        when (section) {
            "Home" -> loadHome(body)
            "Catalogue" -> showCatalogue(body)
            "My Books" -> loadBooks(body)
            "Account" -> showAccount(body)
        }
        scroll.addView(body); content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadHome(body: LinearLayout) {
        body.addView(text("Home", 22f).apply { setTypeface(typeface, 1) })
        body.addView(text("Library announcements and updates", 14f, Color.DKGRAY), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(16)) })
        val card = text("Loading announcements…", 16f); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackgroundColor(Color.WHITE); body.addView(card, LinearLayout.LayoutParams(-1, -2))
        request("/cms/content", "GET", null, sessionToken()) { ok, response -> runOnUiThread {
            if (!ok) { card.text = "Announcements are temporarily unavailable."; return@runOnUiThread }
            try {
                val items = JSONObject(response).optJSONArray("items") ?: JSONArray()
                body.removeView(card)
                if (items.length() == 0) { body.addView(text("No current announcements.", 16f).apply { setPadding(dp(18), dp(18), dp(18), dp(18)); setBackgroundColor(Color.WHITE) }); return@runOnUiThread }
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val title = item.optString("title").ifBlank { item.optString("type").replaceFirstChar { it.uppercase() } }
                    val bodyText = item.optString("body").ifBlank { android.text.Html.fromHtml(item.optString("html"), android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
                    val cardView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); setBackgroundColor(Color.WHITE) }
                    cardView.addView(text(title, 17f).apply { setTypeface(typeface, 1) })
                    if (bodyText.isNotBlank()) cardView.addView(text(bodyText, 14f, Color.DKGRAY), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(7), 0, 0) })
                    body.addView(cardView, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
                }
            } catch (_: Exception) { card.text = "Unable to display announcements." }
        } }
    }

    private fun showCatalogue(body: LinearLayout) {
        body.addView(text("Catalogue", 22f).apply { setTypeface(typeface, 1) })
        val input = EditText(this).apply { hint = "Search books"; setSingleLine(); setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(Color.WHITE) }
        body.addView(input, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(14), 0, dp(10)) })
        val button = MaterialButton(this).apply { text = "Search catalogue"; isAllCaps = false }
        body.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })
        button.setOnClickListener { searchCatalogue(input.text.toString().trim(), results, button) }
        searchCatalogue("", results, button)
    }

    private fun searchCatalogue(query: String, results: LinearLayout, button: MaterialButton) {
        button.isEnabled = false; button.text = "Searching…"; results.removeAllViews(); results.addView(text("Loading catalogue…", 15f))
        request("/catalogue/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"), "GET", null, sessionToken()) { ok, response -> runOnUiThread {
            button.isEnabled = true; button.text = "Search catalogue"; results.removeAllViews()
            if (!ok) { results.addView(text("Catalogue is temporarily unavailable.", 15f)); return@runOnUiThread }
            try {
                val root = JSONObject(response); val arr = root.optJSONArray("items") ?: root.optJSONArray("results") ?: JSONArray()
                if (arr.length() == 0) { results.addView(text("No catalogue records found.", 15f)); return@runOnUiThread }
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    val title = x.optString("title").ifBlank { "Untitled" }; val author = x.optString("author").ifBlank { x.optString("authors") }; val library = x.optString("library")
                    val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(Color.WHITE) }
                    card.addView(text(title, 17f).apply { setTypeface(typeface, 1) }); if (author.isNotBlank()) card.addView(text(author, 14f, Color.DKGRAY)); if (library.isNotBlank()) card.addView(text(library, 13f, Color.DKGRAY))
                    results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
                }
            } catch (_: Exception) { results.addView(text("Unable to read catalogue response.", 15f)) }
        } }
    }

    private fun loadBooks(body: LinearLayout) {
        body.addView(text("My Books", 22f).apply { setTypeface(typeface, 1) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, 0) })
        request("/my-books", "GET", null, sessionToken()) { ok, response -> runOnUiThread {
            if (!ok) { list.addView(text("Unable to load your books.", 15f)); return@runOnUiThread }
            try { val arr = JSONObject(response).optJSONArray("books") ?: JSONArray(); if (arr.length() == 0) list.addView(text("You have no currently issued books.", 15f)) else for (i in 0 until arr.length()) { val x=arr.optJSONObject(i)?:continue; val card=text("${x.optString("title", "Untitled")}\nDue: ${x.optString("date_due", x.optString("due_date", "Not available"))}",16f); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackgroundColor(Color.WHITE); list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))}) } } catch (_: Exception) { list.addView(text("Unable to read your books.")) }
        } }
    }

    private fun showAccount(body: LinearLayout) {
        body.addView(text("Account", 22f).apply { setTypeface(typeface, 1) }); body.addView(text("Signed in as $username()", 16f), LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,dp(20))})
        val history = MaterialButton(this).apply{text="Issue history";isAllCaps=false}; body.addView(history,LinearLayout.LayoutParams(-1,dp(50))); history.setOnClickListener { showHistory() }
        val logout = MaterialButton(this).apply{text="Log out";isAllCaps=false}; body.addView(logout,LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(0,dp(12),0,0)}); logout.setOnClickListener { getSharedPreferences("session",MODE_PRIVATE).edit().clear().apply(); showLogin() }
    }

    private fun showHistory() { showDashboard("My Books"); toast("Issue history is available from My Books in the next update.") }
    private fun request(path: String, method: String, payload: JSONObject?, token: String?, callback: (Boolean, String) -> Unit) { thread { var c: HttpURLConnection?=null; try { c=(URL(gateway+path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=15000;readTimeout=20000;doInput=true;setRequestProperty("Accept","application/json");if(token!=null)setRequestProperty("Authorization","Bearer $token");if(payload!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}}; if(payload!=null)c.outputStream.use{it.write(payload.toString().toByteArray())}; val status=c.responseCode; val s=if(status in 200..299)c.inputStream else c.errorStream; callback(status in 200..299,s?.bufferedReader()?.use{it.readText()}.orEmpty()) } catch(e:Exception){callback(false,e.message.orEmpty())} finally{c?.disconnect()} } }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
