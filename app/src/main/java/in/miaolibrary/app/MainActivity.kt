package `in`.miaolibrary.app

import android.graphics.BitmapFactory
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
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gateway = "https://api.miaolibrary.in"
    private val bg = Color.rgb(248, 245, 239)
    private val ink = Color.rgb(27, 43, 58)
    private val muted = Color.rgb(105, 112, 117)
    private lateinit var content: FrameLayout
    // Exact active-loan identifiers used to keep current loans out of Previous Issues.
    private val currentIssueKeys = mutableSetOf<String>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("session", MODE_PRIVATE)
    private fun token() = prefs().getString("access_token", null)
    private fun username() = prefs().getString("username", "Patron") ?: "Patron"
    private fun memberInfo(): JSONObject? {
        val raw = prefs().getString("member_info", null) ?: return null
        return try { JSONObject(raw) } catch (_: Exception) { null }
    }

    private fun memberPhotoFile() = File(filesDir, "member_photo.jpg")

    private fun hasCompleteMemberInfo(): Boolean {
        val info = memberInfo() ?: return false
        return info.optString("name").isNotBlank() &&
                info.optString("card_number").isNotBlank() &&
                info.has("email") &&
                info.optString("membership_expiry_date").isNotBlank() &&
                info.optString("membership_status").isNotBlank()
    }

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
        } else if (!hasCompleteMemberInfo() || !memberPhotoFile().exists()) {
            // Refresh stale/old cached profile data once. After that, keep it
            // locally until the next logout/login.
            fetchAndStoreMemberData {
                runOnUiThread { dashboard("Home") }
            }
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

                        fetchAndStoreMemberData {
                            runOnUiThread {
                                dashboard("Home")
                            }
                        }
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
        val info = memberInfo() ?: JSONObject()
        val idCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(Color.WHITE, 24)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(4).toFloat()
        }

        val photo = ImageView(this).apply {
            val file = memberPhotoFile()
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { setImageBitmap(it) }
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = shape(Color.rgb(238, 235, 229), 18)
        }

        idCard.addView(photo, LinearLayout.LayoutParams(dp(108), dp(140)))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }

        // These are the exact patron fields returned by the gateway /account contract.
        // Do not substitute Koha HTML-only fields such as category, phone, borrowernumber or username.
        val fullName = first(info, "name")
        val cardNumber = first(info, "card_number")
        val email = first(info, "email")
        val expiry = first(info, "membership_expiry_date")
        val membershipStatus = first(info, "membership_status")

        fun addDetail(label: String, value: String, prominent: Boolean = false) {
            if (value.isBlank() || value == "null") return
            details.addView(
                text("$label: $value", if (prominent) 18f else 13f, if (prominent) ink else muted).apply {
                    setTypeface(typeface, if (prominent) 1 else 0)
                    setPadding(0, if (details.childCount == 0) 0 else dp(7), 0, 0)
                }
            )
        }

        addDetail("Name", fullName, true)
        addDetail("Card No.", cardNumber)
        addDetail("Email", email)
        addDetail("Membership Expiry", expiry)
        addDetail("Membership Status", membershipStatus)

        idCard.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        idCard.setOnClickListener { dashboard("Account") }

        body.addView(idCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(22), 0, dp(24))
        })
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

                    currentIssueKeys.clear()
                    for (i in 0 until items.length()) {
                        items.optJSONObject(i)?.let { currentIssueKeys.addAll(issueKeys(it)) }
                    }

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