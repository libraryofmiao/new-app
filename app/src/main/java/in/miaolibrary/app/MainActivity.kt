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

    private fun text(
        value: String,
        size: Float = 16f,
        color: Int = ink
    ) = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
    }

    private fun shape(
        color: Int = Color.WHITE,
        radius: Int = 20
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun card(view: View) = FrameLayout(this).apply {
        background = shape()
        setPadding(dp(18), dp(16), dp(18), dp(16))
        addView(view, FrameLayout.LayoutParams(-1, -2))
    }

    private fun button(
        label: String,
        primary: Boolean = true
    ) = Button(this).apply {
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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        if (token().isNullOrBlank()) {
            login()
        } else {
            dashboard("Home")
        }
    }

    private fun login() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(bg)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val image = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        box.addView(
            card(image),
            LinearLayout.LayoutParams(-1, dp(150))
        )

        box.addView(
            text("Welcome to Miao Library", 28f).apply {
                gravity = Gravity.CENTER
                setTypeface(typeface, 1)
            },
            LinearLayout.LayoutParams(-1, dp(70))
        )

        box.addView(
            text("Your library, wherever you are", 15f, muted).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, dp(38))
        )

        val user = EditText(this).apply {
            hint = "Library username"
            setSingleLine()
            background = shape()
            setPadding(dp(16), 0, dp(16), 0)
        }

        val pass = EditText(this).apply {
            hint = "Password"
            setSingleLine()
            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = shape()
            setPadding(dp(16), 0, dp(16), 0)
        }

        box.addView(
            user,
            LinearLayout.LayoutParams(-1, dp(56)).apply {
                setMargins(0, dp(12), 0, dp(10))
            }
        )

        box.addView(
            pass,
            LinearLayout.LayoutParams(-1, dp(56))
        )

        val signIn = button("Sign in")

        box.addView(
            signIn,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                setMargins(0, dp(20), 0, 0)
            }
        )

        signIn.setOnClickListener {
            if (user.text.isBlank() || pass.text.isBlank()) {
                toast("Enter your username and password.")
                return@setOnClickListener
            }

            signIn.isEnabled = false
            signIn.text = "Signing in…"

            request(
                "/login",
                "POST",
                JSONObject()
                    .put("username", user.text.toString().trim())
                    .put("password", pass.text.toString()),
                null
            ) { ok, response ->
                runOnUiThread {
                    signIn.isEnabled = true
                    signIn.text = "Sign in"

                    if (!ok) {
                        toast("Login failed.")
                        return@runOnUiThread
                    }

                    try {
                        val j = JSONObject(response)
                        val access =
                            j.optString("access_token")
                                .ifBlank { j.optString("token") }

                        if (access.isBlank()) throw Exception()

                        prefs().edit()
                            .putString("access_token", access)
                            .putString(
                                "username",
                                user.text.toString().trim()
                            )
                            .apply()

                        dashboard("Home")
                    } catch (_: Exception) {
                        toast("Invalid gateway response.")
                    }
                }
            }
        }

        root.addView(
            box,
            LinearLayout.LayoutParams(-1, -2)
        )

        setContentView(root)
    }

    private fun dashboard(section: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }

        header.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.logo)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(dp(64), dp(64))
        )

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        heading.addView(
            text("Miao Library", 23f).apply {
                setTypeface(typeface, 1)
            }
        )

        heading.addView(
            text("Welcome back, ${username()}", 14f, muted)
        )

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(header)

        content = FrameLayout(this)

        root.addView(
            content,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = shape(Color.WHITE, 24)
            elevation = dp(8).toFloat()
        }

        listOf(
            "⌂" to "Home",
            "⌕" to "Catalogue",
            "▣" to "My Books",
            "●" to "Account"
        ).forEach { pair ->
            val selected = pair.second == section

            val icon = text(
                pair.first,
                30f,
                if (selected) Color.WHITE else ink
            ).apply {
                gravity = Gravity.CENTER
                includeFontPadding = true
            }

            val label = text(
                pair.second,
                14f,
                if (selected) Color.WHITE else ink
            ).apply {
                gravity = Gravity.CENTER
            }

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background =
                    if (selected) shape(ink, 18) else null
                setPadding(dp(3), dp(4), dp(3), dp(4))

                addView(
                    icon,
                    LinearLayout.LayoutParams(-1, dp(36))
                )

                addView(
                    label,
                    LinearLayout.LayoutParams(-1, dp(25))
                )

                setOnClickListener {
                    dashboard(pair.second)
                }
            }

            nav.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(76), 1f)
            )
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(-1, dp(92))
        )

        setContentView(root)
        showSection(section)
    }

    private fun showSection(section: String) {
        content.removeAllViews()

        val scroll = ScrollView(this)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(36))
        }

        // The Home heading is intentionally removed.
        if (section != "Home") {
            body.addView(
                text(section, 26f).apply {
                    gravity = Gravity.CENTER
                    setTypeface(typeface, 1)
                    setPadding(0, dp(8), 0, dp(8))
                }
            )
        }

        when (section) {
            "Home" -> home(body)
            "Catalogue" -> catalogue(body)
            "My Books" -> books(body)
            "Account" -> account(body)
        }

        scroll.addView(body)
        content.addView(scroll)
    }

    private fun home(body: LinearLayout) {
    body.addView(text("A welcoming space for learning, discovery and connection", 16f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(16)) })
    val exploreText = text("Arunachal Pradesh's first futuristic New Age Learning Centre — a paradise for book lovers, competitive aspirants, and lifelong learners nestled in the heart of Miao, Changlang district.", 16f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
    exploreText.textAlignment = View.TEXT_ALIGNMENT_CENTER
    body.addView(card(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(exploreText) }), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16)) })
    val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val c = button("Catalogue", true).apply { textSize = 15f }
    val b = button("My Books", true).apply { textSize = 15f }
    quick.addView(c, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(0, 0, dp(6), 0) })
    quick.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(6), 0, 0, 0) })
    c.setOnClickListener { dashboard("Catalogue") }; b.setOnClickListener { dashboard("My Books") }; body.addView(quick)
    body.addView(text("Library updates", 20f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1); setPadding(0, dp(24), 0, dp(10)) })
    val loading = card(text("Loading announcements…", 14f, muted)); body.addView(loading)
    request("/cms/content", "GET", null, token()) { ok, response -> runOnUiThread { body.removeView(loading); if (!ok) body.addView(card(text("Announcements are temporarily unavailable.", 14f, muted))) else { val items = arrayFrom(response, "items", "content", "announcements"); if (items.length() == 0) body.addView(card(text("No current announcements.", 14f, muted))) else for (i in 0 until items.length()) { val item = items.optJSONObject(i) ?: continue; val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(first(item, "title", "name").ifBlank { "Published announcement" }, 17f).apply { setTypeface(typeface, 1) }); addView(text(first(item, "body", "description", "html", "content"), 14f, muted).apply { setPadding(0, dp(8), 0, 0) }) }; body.addView(card(box), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }) } } } }
}

    private fun catalogue(body: LinearLayout) {
        val input = EditText(this).apply {
            hint = "Search books, authors or subjects"
            setSingleLine()
            background = shape()
            setPadding(dp(16), 0, dp(16), 0)
        }

        body.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(56)).apply {
                setMargins(0, dp(14), 0, dp(10))
            }
        )

        val search = button("Search catalogue")

        body.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        val output = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(
            output,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(18), 0, 0)
            }
        )

        search.setOnClickListener {
            searchCatalogue(
                input.text.toString(),
                output,
                search
            )
        }

        searchCatalogue("", output, search)
    }

    private fun searchCatalogue(
        query: String,
        output: LinearLayout,
        search: Button
    ) {
        search.isEnabled = false
        output.removeAllViews()

        output.addView(
            text("Searching catalogue…", 14f, muted)
        )

        request(
            "/catalogue/search?q=" +
                    URLEncoder.encode(query, "UTF-8"),
            "GET",
            null,
            token()
        ) { ok, response ->
            runOnUiThread {
                search.isEnabled = true
                output.removeAllViews()

                if (!ok) {
                    output.addView(
                        card(
                            text(
                                "Catalogue unavailable.",
                                14f,
                                muted
                            )
                        )
                    )
                    return@runOnUiThread
                }

                val items = arrayFrom(
                    response,
                    "items",
                    "results",
                    "records",
                    "books"
                )

                if (items.length() == 0) {
                    output.addView(
                        card(
                            text(
                                "No catalogue records found.",
                                14f,
                                muted
                            )
                        )
                    )
                } else {
                    for (i in 0 until items.length()) {
                        val item =
                            items.optJSONObject(i) ?: continue

                        val box = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL

                            addView(
                                text(
                                    first(item, "title").ifBlank {
                                        "Untitled"
                                    },
                                    18f
                                ).apply {
                                    setTypeface(typeface, 1)
                                }
                            )

                            addIfPresent(
                                this,
                                "Author",
                                item,
                                "author"
                            )

                            addIfPresent(
                                this,
                                "Library",
                                item,
                                "library"
                            )

                            addIfPresent(
                                this,
                                "Availability",
                                item,
                                "availability"
                            )

                            addIfPresent(
                                this,
                                "Call number",
                                item,
                                "call_number"
                            )

                            addIfPresent(
                                this,
                                "Barcode",
                                item,
                                "barcode"
                            )

                            addIfPresent(
                                this,
                                "Holdings",
                                item,
                                "holding_count"
                            )

                            addView(
                                text(
                                    "Tap for complete book details",
                                    12f,
                                    ink
                                ).apply {
                                    setPadding(0, dp(12), 0, 0)
                                }
                            )
                        }

                        val itemCard = card(box).apply {
                            background =
                                GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                    cornerRadius =
                                        dp(20).toFloat()
                                    setStroke(dp(1), ink)
                                }

                            elevation = dp(2).toFloat()
                        }

                        itemCard.setOnClickListener {
                            openDetails(item)
                        }

                        output.addView(
                            itemCard,
                            LinearLayout.LayoutParams(-1, -2).apply {
                                setMargins(0, 0, 0, dp(12))
                            }
                        )
                    }
                }
            }
        }
    }

    private fun addIfPresent(
        parent: LinearLayout,
        label: String,
        item: JSONObject,
        key: String
    ) {
        val value = first(item, key)

        if (value.isNotBlank()) {
            parent.addView(
                text("$label: $value", 13f, muted)
            )
        }
    }

    private fun openDetails(item: JSONObject) {
        val id = first(item, "biblionumber")

        if (id.isBlank()) {
            details(item)
            return
        }

        request(
            "/book-details/$id",
            "GET",
            null,
            token()
        ) { ok, response ->
            runOnUiThread {
                if (ok) {
                    try {
                        val j = JSONObject(response)

                        details(
                            j.optJSONObject("book")
                                ?: j.optJSONObject("record")
                                ?: j
                        )
                    } catch (_: Exception) {
                        details(item)
                    }
                } else {
                    details(item)
                }
            }
        }
    }

    private fun details(item: JSONObject) {
        dashboard("Catalogue")
        content.removeAllViews()

        val scroll = ScrollView(this)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(30))
        }

        body.addView(
            text("Book details", 25f).apply {
                setTypeface(typeface, 1)
            }
        )

        arrayOf(
            "title",
            "subtitle",
            "author",
            "authors",
            "publisher",
            "publication",
            "publication_year",
            "year",
            "isbn",
            "isbn13",
            "isbn10",
            "call_number",
            "callnumber",
            "shelfmark",
            "library",
            "location",
            "availability",
            "status",
            "barcode",
            "holding_count",
            "copy_count",
            "holdings",
            "item_type",
            "notes",
            "biblionumber"
        ).forEach { field ->
            val value = displayValue(item, field)

            if (value.isNotBlank()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL

                    addView(
                        text(
                            field
                                .replace('_', ' ')
                                .replaceFirstChar {
                                    it.uppercase()
                                },
                            12f,
                            muted
                        )
                    )

                    addView(
                        text(value, 16f).apply {
                            setPadding(0, dp(4), 0, 0)
                        }
                    )
                }

                body.addView(
                    card(row),
                    LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, 0, 0, dp(9))
                    }
                )
            }
        }

        scroll.addView(body)
        content.addView(scroll)
    }

    private fun books(body: LinearLayout) {
        body.addView(
            text("Currently issued", 19f).apply {
                setTypeface(typeface, 1)
                setPadding(0, dp(14), 0, dp(10))
            }
        )

        val current = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(current)

        request(
            "/my-books",
            "GET",
            null,
            token()
        ) { ok, response ->
            runOnUiThread {
                if (!ok) {
                    current.addView(
                        card(
                            text(
                                "Unable to load your books.",
                                14f,
                                muted
                            )
                        )
                    )
                } else {
                    val items = arrayFrom(
                        response,
                        "books",
                        "items",
                        "issues",
                        "current",
                        "current_books"
                    )

                    if (items.length() == 0) {
                        current.addView(
                            card(
                                text(
                                    "You have no currently issued books.",
                                    14f,
                                    muted
                                )
                            )
                        )
                    } else {
                        records(items, current, true)
                    }
                }
            }
        }

        body.addView(
            text("Previous issues", 19f).apply {
                setTypeface(typeface, 1)
                setPadding(0, dp(24), 0, dp(10))
            }
        )

        val history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(history)

        val historyButton = button(
            "Load Issue History / Previous Issues",
            true
        ).apply {
            setTextColor(Color.WHITE)
            background = shape(Color.rgb(72, 105, 112), 16)
            textSize = 14f
        }

        body.addView(
            historyButton,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                setMargins(0, 0, 0, dp(12))
            }
        )

        history.addView(
            card(
                text(
                    "Press the button to load your previous issued books.",
                    14f,
                    muted
                )
            )
        )

        historyButton.setOnClickListener {
            loadHistory(history)
        }
    }

    private fun loadHistory(output: LinearLayout) {
        output.removeAllViews()

        output.addView(
            card(
                text(
                    "Loading previous issues…",
                    14f,
                    muted
                )
            )
        )

        request(
            "/issue-history",
            "GET",
            null,
            token()
        ) { ok, response ->
            runOnUiThread {
                output.removeAllViews()

                if (!ok) {
                    output.addView(
                        card(
                            text(
                                "Unable to load issue history.",
                                14f,
                                muted
                            )
                        )
                    )
                    return@runOnUiThread
                }

                val items = arrayFrom(
                    response,
                    "items",
                    "history",
                    "issues",
                    "books",
                    "previous_issues",
                    "previousIssues",
                    "returned_books",
                    "past_issues",
                    "circulation_history",
                    "circulationHistory",
                    "issue_history",
                    "issueHistory",
                    "results",
                    "records",
                    "data"
                )

                if (items.length() == 0) {
                    output.addView(
                        card(
                            text(
                                "No previous issues found.",
                                14f,
                                muted
                            )
                        )
                    )
                } else {
                    records(items, output, false)
                }
            }
        }
    }

    private fun records(
        items: JSONArray,
        output: LinearLayout,
        current: Boolean
    ) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (!current && !isReturnedIssue(item)) continue

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL

                addView(
                    text(
                        first(
                            item,
                            "title",
                            "name"
                        ).ifBlank {
                            "Untitled"
                        },
                        17f
                    ).apply {
                        setTypeface(typeface, 1)
                    }
                )

                addIfPresent(
                    this,
                    "Author",
                    item,
                    "author"
                )

                addIfPresent(
                    this,
                    "Library",
                    item,
                    "library"
                )

                addFirstPresent(
                    this,
                    "Issued date",
                    item,
                    "checkout_date",
                    "date_issued",
                    "issued_date",
                    "issue_date",
                    "date_checkout"
                )

                addFirstPresent(
                    this,
                    "Due date",
                    item,
                    "date_due",
                    "due_date",
                    "duedate",
                    "due"
                )

                addFirstPresent(
                    this,
                    "Renewed date",
                    item,
                    "date_renewed",
                    "renewed_date",
                    "renewal_date",
                    "renewed_until"
                )

                addFirstPresent(
                    this,
                    "Renewals",
                    item,
                    "renewals",
                    "renewal_count"
                )

                addFirstPresent(
                    this,
                    "Returned date",
                    item,
                    "date_returned",
                    "returned_date",
                    "return_date",
                    "checkin_date",
                    "date_checkin"
                )

                addIfPresent(
                    this,
                    "Barcode",
                    item,
                    "barcode"
                )
            }

            output.addView(
                card(box),
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, 0, 0, dp(12))
                }
            )
        }
    }) return true

        val status = first(item, "status", "issue_status", "item_status", "loan_status").lowercase()
        if (status.contains("return") || status.contains("checkin") || status.contains("closed")) return true
        if (status.contains("issue") || status.contains("checkout") || status.contains("loan") || status.contains("out")) return false
        return false
    }

    private fun account(body: LinearLayout) {
    body.addView(card(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; addView(text("Library account", 21f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1) }); addView(text(username(), 16f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }); addView(text("Your Koha patron account", 13f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }) }), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, dp(16)) })
    val my = button("View my books", false).apply { textSize = 15f }; body.addView(my, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) }); my.setOnClickListener { dashboard("My Books") }
    val refresh = button("Refresh account", false).apply { textSize = 15f }; body.addView(refresh, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) }); refresh.setOnClickListener { dashboard("Account") }
    val logout = button("Log out", true).apply { background = shape(Color.rgb(155, 76, 76), 16); textSize = 15f }; body.addView(logout, LinearLayout.LayoutParams(-1, dp(52))); logout.setOnClickListener { prefs().edit().clear().apply(); login() }

    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, dp(34), 0, dp(12))
    }
    val website = text("Our Official Website : miaolibrary.in", 14f, ink).apply {
        gravity = Gravity.CENTER
        setTypeface(typeface, 1)
        setOnClickListener {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://miaolibrary.in")))
        }
    }
    footer.addView(website)
    footer.addView(text("© Sub Divisional Library, Miao. All Rights Reserved.", 12f, muted).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, 0)
    })
    body.addView(footer)
}

    private fun displayValue(
        item: JSONObject,
        key: String
    ): String {
        val value = item.opt(key) ?: return ""

        if (value is JSONArray) {
            return prettyArray(value)
        }

        if (value is JSONObject) {
            return value.toString()
        }

        return value.toString().trim()
    }

    private fun prettyArray(array: JSONArray): String {
        val lines = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)

            if (obj != null) {
                val keys = listOf(
                    "item_type",
                    "current_library",
                    "home_library",
                    "collection",
                    "shelving_location",
                    "call_number",
                    "copy_number",
                    "status",
                    "notes",
                    "date_due",
                    "barcode"
                )

                val parts = keys.mapNotNull { key ->
                    obj.optString(key)
                        .takeIf { it.isNotBlank() }
                        .let {
                            if (it == null) {
                                null
                            } else {
                                "${key.replace('_', ' ')}: $it"
                            }
                        }
                }

                lines.add(
                    if (parts.isEmpty()) {
                        obj.toString()
                    } else {
                        parts.joinToString("\n")
                    }
                )
            } else {
                lines.add(array.optString(i))
            }
        }

        return lines.joinToString("\n\n")
    }

    private fun first(
        item: JSONObject,
        vararg keys: String
    ): String {
        for (key in keys) {
            val value = displayValue(item, key)

            if (value.isNotBlank() && value != "null") {
                return value
            }
        }

        return ""
    }

    private fun arrayFrom(
        raw: String,
        vararg keys: String
    ): JSONArray {
        return try {
            val trimmed = raw.trim()

            if (trimmed.startsWith("[")) {
                return JSONArray(trimmed)
            }

            val root = JSONObject(trimmed)

            for (key in keys) {
                val direct = root.opt(key)

                if (direct is JSONArray) {
                    return direct
                }

                if (direct is JSONObject) {
                    val nested = findArray(
                        direct,
                        keys.toSet()
                    )

                    if (nested.length() > 0) {
                        return nested
                    }
                }
            }

            findArray(root, keys.toSet())
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun findArray(
        obj: JSONObject,
        wanted: Set<String>
    ): JSONArray {
        val preferred = listOf(
            "items",
            "history",
            "issues",
            "books",
            "previous_issues",
            "previousIssues",
            "returned_books",
            "past_issues",
            "results",
            "records",
            "data"
        )

        for (key in preferred) {
            val value = obj.opt(key)

            if (
                value is JSONArray &&
                (wanted.contains(key) ||
                        key == "data" ||
                        key == "items")
            ) {
                return value
            }

            if (value is JSONObject) {
                val nested = findArray(value, wanted)

                if (nested.length() > 0) {
                    return nested
                }
            }
        }

        val names = obj.keys()

        while (names.hasNext()) {
            val key = names.next()
            val value = obj.opt(key)

            if (
                value is JSONArray &&
                value.length() > 0 &&
                (
                        key.lowercase().contains("histor") ||
                                key.lowercase().contains("issue") ||
                                key.lowercase().contains("book")
                        )
            ) {
                return value
            }

            if (value is JSONObject) {
                val nested = findArray(value, wanted)

                if (nested.length() > 0) {
                    return nested
                }
            }
        }

        return JSONArray()
    }

    private fun request(
        path: String,
        method: String,
        payload: JSONObject?,
        auth: String?,
        callback: (Boolean, String) -> Unit
    ) {
        thread {
            var connection: HttpURLConnection? = null

            try {
                connection =
                    (URL(gateway + path).openConnection()
                            as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = 15000
                        readTimeout = 20000

                        setRequestProperty(
                            "Accept",
                            "application/json"
                        )

                        if (auth != null) {
                            setRequestProperty(
                                "Authorization",
                                "Bearer $auth"
                            )
                        }

                        if (payload != null) {
                            doOutput = true

                            setRequestProperty(
                                "Content-Type",
                                "application/json"
                            )
                        }
                    }

                if (payload != null) {
                    connection.outputStream.use {
                        it.write(
                            payload.toString().toByteArray()
                        )
                    }
                }

                val code = connection.responseCode

                val stream =
                    if (code in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response =
                    stream?.bufferedReader()?.use {
                        it.readText()
                    }.orEmpty()

                callback(code in 200..299, response)
            } catch (error: Exception) {
                callback(false, error.message.orEmpty())
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
}