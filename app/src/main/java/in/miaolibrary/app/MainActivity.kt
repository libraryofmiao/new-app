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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gateway = "https://api.miaolibrary.in"
    private val bg = Color.rgb(247, 243, 236)
    private val navy = Color.rgb(28, 45, 63)
    private val blue = Color.rgb(45, 83, 111)
    private lateinit var content: FrameLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 16f, color: Int = navy) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun prefs() = getSharedPreferences("session", MODE_PRIVATE)
    private fun token() = prefs().getString("access_token", null)
    private fun username() = prefs().getString("username", "Patron") ?: "Patron"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (token().isNullOrBlank()) showLogin() else showDashboard("Home")
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(20), dp(28), dp(28)); setBackgroundColor(bg) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(label("Miao Library", 30f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1) }, LinearLayout.LayoutParams(-1, dp(80)))
        box.addView(label("Sign in to access your library account", 15f, Color.DKGRAY).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(50)))
        val user = EditText(this).apply { hint = "Library username"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT }
        val pass = EditText(this).apply { hint = "Password"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(user, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(10), 0, 0) })
        box.addView(pass, LinearLayout.LayoutParams(-1, dp(56)))
        val button = Button(this).apply { text = "Sign in"; isAllCaps = false }
        box.addView(button, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(20), 0, 0) })
        button.setOnClickListener {
            val u = user.text.toString().trim(); val p = pass.text.toString()
            if (u.isBlank() || p.isBlank()) { toast("Enter your username and password."); return@setOnClickListener }
            button.isEnabled = false; button.text = "Signing in…"
            request("/login", "POST", JSONObject().put("username", u).put("password", p), null) { ok, body -> runOnUiThread {
                button.isEnabled = true; button.text = "Sign in"
                if (!ok) { toast("Login failed. Please check your credentials."); return@runOnUiThread }
                try {
                    val j = JSONObject(body); val t = j.optString("access_token").ifBlank { j.optString("token") }
                    if (t.isBlank()) throw Exception()
                    prefs().edit().putString("access_token", t).putString("username", u).apply(); showDashboard("Home")
                } catch (_: Exception) { toast("Invalid gateway response.") }
            } }
        }
        box.setPadding(dp(4), dp(20), dp(4), dp(20)); root.addView(box, LinearLayout.LayoutParams(-1, -2)); setContentView(root)
    }

    private fun showDashboard(section: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(10)) }
        top.addView(label("Miao Library", 27f).apply { setTypeface(typeface, 1) })
        top.addView(label("Welcome back, ${username()}", 14f, Color.DKGRAY))
        root.addView(top)
        content = FrameLayout(this); root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); elevation = dp(8).toFloat() }
        listOf("Home", "Catalogue", "My Books", "Account").forEach { name ->
            val item = TextView(this).apply { text = name; textSize = 12f; gravity = Gravity.CENTER; setTextColor(blue); isClickable = true; isFocusable = true; setOnClickListener { showDashboard(name) } }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(62))); setContentView(root); showSection(section)
    }

    private fun showSection(section: String) {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(10), dp(22), dp(24)) }
        body.addView(label(section, 22f).apply { setTypeface(typeface, 1) })
        when (section) { "Home" -> loadHome(body); "Catalogue" -> showCatalogue(body); "My Books" -> loadBooks(body); "Account" -> showAccount(body) }
        scroll.addView(body); content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadHome(body: LinearLayout) {
        body.addView(label("Library announcements and updates", 14f, Color.DKGRAY))
        val card = label("Loading announcements…"); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackgroundColor(Color.WHITE); body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })
        request("/cms/content", "GET", null, token()) { ok, response -> runOnUiThread {
            if (!ok) { card.text = "Announcements are temporarily unavailable."; return@runOnUiThread }
            try {
                val arr = JSONObject(response).optJSONArray("items") ?: JSONArray(); body.removeView(card)
                if (arr.length() == 0) { body.addView(label("No current announcements.")); return@runOnUiThread }
                for (i in 0 until arr.length()) { val x = arr.optJSONObject(i) ?: continue; val title = x.optString("title").ifBlank { "Announcement" }; val text = x.optString("body").ifBlank { x.optString("html") }; val v = label("$title\n$text", 15f); v.setPadding(dp(18), dp(16), dp(18), dp(16)); v.setBackgroundColor(Color.WHITE); body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }) }
            } catch (_: Exception) { card.text = "Unable to display announcements." }
        } }
    }

    private fun showCatalogue(body: LinearLayout) {
        val input = EditText(this).apply { hint = "Search books"; setSingleLine() }; body.addView(input, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(14), 0, dp(10)) })
        val button = Button(this).apply { text = "Search catalogue"; isAllCaps = false }; body.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(results, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })
        button.setOnClickListener { searchCatalogue(input.text.toString().trim(), results, button) }; searchCatalogue("", results, button)
    }

    private fun searchCatalogue(q: String, results: LinearLayout, button: Button) {
        button.isEnabled = false; results.removeAllViews(); results.addView(label("Loading catalogue…"))
        request("/catalogue/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), "GET", null, token()) { ok, response -> runOnUiThread {
            button.isEnabled = true; results.removeAllViews(); if (!ok) { results.addView(label("Catalogue is temporarily unavailable.")); return@runOnUiThread }
            try { val arr = JSONObject(response).optJSONArray("items") ?: JSONObject(response).optJSONArray("results") ?: JSONArray(); if (arr.length() == 0) results.addView(label("No catalogue records found.")) else for (i in 0 until arr.length()) { val x=arr.optJSONObject(i)?:continue; val v=label("${x.optString("title", "Untitled")}\n${x.optString("author", x.optString("authors"))}\n${x.optString("library")}",15f); v.setPadding(dp(16),dp(14),dp(16),dp(14)); v.setBackgroundColor(Color.WHITE); results.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))}) } } catch (_: Exception) { results.addView(label("Unable to read catalogue response.")) }
        } }
    }

    private fun loadBooks(body: LinearLayout) {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, 0) })
        request("/my-books", "GET", null, token()) { ok, response -> runOnUiThread { if (!ok) { list.addView(label("Unable to load your books.")); return@runOnUiThread }; try { val arr=JSONObject(response).optJSONArray("books")?:JSONArray(); if(arr.length()==0) list.addView(label("You have no currently issued books.")) else for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val v=label("${x.optString("title","Untitled")}\nDue: ${x.optString("date_due",x.optString("due_date","Not available"))}",15f);v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackgroundColor(Color.WHITE);list.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))})}}catch(_:Exception){list.addView(label("Unable to read your books."))} } }
    }

    private fun showAccount(body: LinearLayout) {
        body.addView(label("Signed in as ${username()}", 16f), LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(14),0,dp(20)) })
        val history = Button(this).apply { text = "Issue history"; isAllCaps = false }; body.addView(history, LinearLayout.LayoutParams(-1,dp(50))); history.setOnClickListener { loadHistory() }
        val logout = Button(this).apply { text = "Log out"; isAllCaps = false }; body.addView(logout, LinearLayout.LayoutParams(-1,dp(50)).apply { setMargins(0,dp(12),0,0) }); logout.setOnClickListener { prefs().edit().clear().apply(); showLogin() }
    }

    private fun loadHistory() { showDashboard("My Books"); toast("Issue history can be viewed from the My Books section.") }
    private fun request(path:String, method:String, payload:JSONObject?, auth:String?, cb:(Boolean,String)->Unit) { thread { var c:HttpURLConnection?=null; try { c=(URL(gateway+path).openConnection() as HttpURLConnection).apply { requestMethod=method; connectTimeout=15000; readTimeout=20000; setRequestProperty("Accept","application/json"); if(auth!=null)setRequestProperty("Authorization","Bearer $auth"); if(payload!=null){doOutput=true;setRequestProperty("Content-Type","application/json")} }; if(payload!=null)c.outputStream.use{it.write(payload.toString().toByteArray())}; val code=c.responseCode; val stream=if(code in 200..299)c.inputStream else c.errorStream; cb(code in 200..299,stream?.bufferedReader()?.use{it.readText()}.orEmpty()) } catch(e:Exception){cb(false,e.message.orEmpty())} finally{c?.disconnect()} } }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
