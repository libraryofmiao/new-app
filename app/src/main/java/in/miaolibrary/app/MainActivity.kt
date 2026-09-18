package `in`.miaolibrary.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
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
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import com.caverock.androidsvg.SVG

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

    private fun memberField(info: JSONObject, vararg keys: String): String {
        fun find(obj: JSONObject, depth: Int): String {
            if (depth > 3) return ""
            for (key in keys) {
                val value = obj.opt(key)
                if (value != null && value !is JSONObject && value !is JSONArray) {
                    val text = value.toString().trim()
                    if (text.isNotBlank() && text != "null") return text
                }
            }
            val names = obj.keys()
            while (names.hasNext()) {
                val value = obj.opt(names.next())
                if (value is JSONObject) {
                    val found = find(value, depth + 1)
                    if (found.isNotBlank()) return found
                }
            }
            return ""
        }
        return find(info, 0)
    }

    private fun memberPhotoFile() = File(filesDir, "member_photo.jpg")

    private fun hasCompleteMemberInfo(): Boolean {
        val info = memberInfo() ?: return false
        return memberField(info, "name").isNotBlank() &&
                memberField(info, "card_number").isNotBlank() &&
                memberField(info, "membership_expiry_date").isNotBlank() &&
                memberField(info, "membership_status").isNotBlank()
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
                runOnUiThread {
                    dashboard("Home")
                    syncDueDateNotifications()
                }
            }
        } else {
            dashboard("Home")
            syncDueDateNotifications()
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
                                syncDueDateNotifications()
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

    private fun libraryNavIcon(assetName: String): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        try {
            val svg = SVG.getFromAsset(assets, "advanced_library_icons/$assetName.svg")
            val picture = svg.renderToPicture()

            // Render and crop transparent SVG margins so the actual artwork,
            // rather than the padded SVG viewBox, fills the icon area.
            val sourceW = picture.width.coerceAtLeast(1)
            val sourceH = picture.height.coerceAtLeast(1)
            val renderSize = 512
            val scale = minOf(
                renderSize.toFloat() / sourceW,
                renderSize.toFloat() / sourceH
            )
            val renderedW = (sourceW * scale).toInt().coerceAtLeast(1)
            val renderedH = (sourceH * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(renderedW, renderedH, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                scale(scale, scale)
                drawPicture(picture)
            }

            var left = renderedW
            var top = renderedH
            var right = -1
            var bottom = -1

            for (y in 0 until renderedH) {
                for (x in 0 until renderedW) {
                    if (bitmap.getPixel(x, y) ushr 24 > 8) {
                        if (x < left) left = x
                        if (y < top) top = y
                        if (x > right) right = x
                        if (y > bottom) bottom = y
                    }
                }
            }

            if (right >= left && bottom >= top) {
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    left,
                    top,
                    right - left + 1,
                    bottom - top + 1
                )
                if (cropped !== bitmap) bitmap.recycle()
                setImageBitmap(cropped)
            } else {
                setImageBitmap(bitmap)
            }
        } catch (_: Exception) {
            setImageResource(R.drawable.logo)
        }
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

        val firstName = memberField(memberInfo() ?: JSONObject(), "name")
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: username()

        heading.addView(
            text("Welcome back, $firstName", 14f, muted)
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
            "home" to "Home",
            "catalog" to "Catalogue",
            "my-books" to "My Books",
            "account" to "Account"
        ).forEach { pair ->
            val selected = pair.second == section

            val icon = libraryNavIcon(pair.first).apply {
                alpha = if (selected) 1f else 0.78f
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
                    if (selected) shape(Color.rgb(232, 228, 220), 18) else null
                setPadding(dp(3), dp(4), dp(3), dp(4))

                addView(
                    icon,
                    LinearLayout.LayoutParams(-1, dp(20))
                )

                addView(
                    label,
                    LinearLayout.LayoutParams(-1, dp(22))
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

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

        val fullName = memberField(info, "name")
        val cardNumber = memberField(info, "card_number")
        val email = memberField(info, "email")
        val expiry = memberField(info, "membership_expiry_date")
        val membershipStatus = memberField(info, "membership_status")

        fun addDetail(display: String, prominent: Boolean = false) {
            if (display.isBlank() || display == "null") return
            details.addView(
                text(
                    display,
                    if (prominent) 18f else 13f,
                    if (prominent) ink else muted
                ).apply {
                    setTypeface(typeface, if (prominent) 1 else 0)
                    setPadding(0, if (details.childCount == 0) 0 else dp(7), 0, 0)
                }
            )
        }

        addDetail(fullName, true)
        addDetail("Card No.: $cardNumber")
        addDetail("Email: $email")
        addDetail("Valid Upto : ${formatMembershipExpiry(expiry)}")
        addDetail("Membership Status: $membershipStatus")

        idCard.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        idCard.setOnClickListener { dashboard("Account") }

        body.addView(
            idCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(22), 0, dp(18))
            }
        )

        // Invisible flexible space keeps published announcements at the bottom
        // of the Home viewport. With no published content, no announcement
        // container or label is created at all.
        body.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        request("/cms/content", "GET", null, token()) { ok, response ->
            if (!ok) return@request

            runOnUiThread {
                val rawItems = arrayFrom(response, "items", "content", "announcements")
                val publishedItems = mutableListOf<JSONObject>()
                for (i in 0 until rawItems.length()) {
                    val item = rawItems.optJSONObject(i) ?: continue
                    if (isPublishedAnnouncement(item)) publishedItems.add(item)
                }

                if (publishedItems.isEmpty()) return@runOnUiThread

                val announcementArea = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(8), 0, dp(18))
                }

                announcementArea.addView(
                    text("Announcements", 19f).apply {
                        gravity = Gravity.CENTER
                        setTypeface(typeface, 1)
                        setPadding(0, 0, 0, dp(10))
                    }
                )

                for (item in publishedItems) {
                    val title = first(item, "title", "name").ifBlank { "Published announcement" }
                    val bodyText = first(item, "body", "description", "html", "content")

                    val box = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    box.addView(text(title, 17f).apply {
                        gravity = Gravity.CENTER
                        setTypeface(typeface, 1)
                    })
                    if (bodyText.isNotBlank()) {
                        box.addView(text(bodyText, 14f, muted).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, 0)
                        })
                    }
                    announcementArea.addView(
                        card(box),
                        LinearLayout.LayoutParams(-1, -2).apply {
                            setMargins(0, 0, 0, dp(10))
                        }
                    )
                }

                body.addView(announcementArea, LinearLayout.LayoutParams(-1, -2))
            }
        }
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
            if (!current && isCurrentIssue(item)) continue

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
    }

    private fun isCurrentIssue(item: JSONObject): Boolean {
        if (currentIssueKeys.isEmpty()) return false
        return issueKeys(item).any { currentIssueKeys.contains(it) }
    }

    private fun issueKeys(item: JSONObject): Set<String> {
        val keys = mutableSetOf<String>()

        fun add(prefix: String, value: String) {
            val v = value.trim()
            if (v.isNotBlank() && v != "null") keys.add("$prefix:$v")
        }

        add("issue", first(item, "issue_id", "issueid", "issueId", "checkout_id", "checkoutId", "loan_id", "loanId"))

        val barcode = first(item, "barcode", "item_barcode")
        val issued = first(item, "checkout_date", "date_issued", "issued_date", "issue_date", "date_checkout")
        val due = first(item, "date_due", "due_date", "duedate", "due")

        if (barcode.isNotBlank() && issued.isNotBlank()) add("barcode-issued", "$barcode|$issued")
        if (barcode.isNotBlank() && due.isNotBlank()) add("barcode-due", "$barcode|$due")

        return keys
    }

    private fun isReturnedIssue(item: JSONObject): Boolean {
        val returnedKeys = listOf("date_returned", "returned_date", "return_date", "checkin_date", "date_checkin")
        if (returnedKeys.any {
                val value = item.optString(it, "").trim()
                value.isNotBlank() && value != "null"
            }) return true

        val status = first(item, "status", "issue_status", "item_status", "loan_status").lowercase()
        if (status.contains("return") || status.contains("checkin") || status.contains("closed")) return true
        if (status.contains("issue") || status.contains("checkout") || status.contains("loan") || status.contains("out")) return false
        return false
    }

    private fun account(body: LinearLayout) {
        body.addView(
            card(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(text("Library account", 21f).apply {
                        gravity = Gravity.CENTER
                        setTypeface(typeface, 1)
                    })
                    addView(text(username(), 16f, muted).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, 0)
                    })
                    addView(text("Your Koha patron account", 13f, muted).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(4), 0, 0)
                    })
                }
            ),
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(14), 0, dp(16))
            }
        )

        val refresh = button("Refresh account", false).apply { textSize = 15f }
        body.addView(refresh, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(0, 0, 0, dp(10))
        })
        refresh.setOnClickListener { dashboard("Account") }

        val logout = button("Log out", true).apply {
            background = shape(Color.rgb(155, 76, 76), 16)
            textSize = 15f
        }
        body.addView(logout, LinearLayout.LayoutParams(-1, dp(52)))
        logout.setOnClickListener {
            prefs().edit().clear().apply()
            memberPhotoFile().delete()
            File(filesDir, "member_photo.tmp").delete()
            login()
        }

        // Flexible space pins the complete footer to the bottom of Account.
        body.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, dp(12))
        }

        footer.addView(text("Our Official Website : miaolibrary.in", 14f, ink).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, 1)
            setOnClickListener {
                startActivity(android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://miaolibrary.in")
                ))
            }
        })
        footer.addView(text("Developed By : A. M. Tripathi", 13f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        footer.addView(text("© Sub Divisional Library Miao. All Rights Reserved.", 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        body.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }
    private fun syncDueDateNotifications() {
        val access = token() ?: return
        request("/my-books", "GET", null, access) { ok, response ->
            if (!ok) return@request
            val items = arrayFrom(response, "books", "items", "issues", "current", "current_books")
            DueDateNotificationScheduler.sync(this, items)
        }
    }

    private fun formatMembershipExpiry(value: String): String {
        val raw = value.trim()
        if (raw.isBlank() || raw == "null") return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val date = parser.parse(raw) ?: return raw
            SimpleDateFormat("dd MMMM yyyy", Locale.US).format(date)
        } catch (_: Exception) {
            raw
        }
    }

    private fun isPublishedAnnouncement(item: JSONObject): Boolean {
        val published = item.opt("published")
        if (published is Boolean && !published) return false
        if (published is String && published.trim().lowercase() in setOf("false", "0", "no", "draft", "unpublished")) return false

        val isPublished = item.opt("is_published")
        if (isPublished is Boolean && !isPublished) return false
        if (isPublished is String && isPublished.trim().lowercase() in setOf("false", "0", "no")) return false

        val status = first(item, "status", "publish_status", "publication_status").lowercase()
        if (status in setOf("draft", "unpublished", "inactive", "archived", "deleted")) return false
        return true
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

    private fun fetchAndStoreMemberData(done: () -> Unit) {
        val access = token()
        if (access.isNullOrBlank()) {
            done()
            return
        }

        thread {
            request("/account", "GET", null, access) { ok, response ->
                if (ok) {
                    prefs().edit().putString("member_info", response).apply()
                }

                fetchAndStoreMemberPhoto(access) {
                    done()
                }
            }
        }
    }

    private fun fetchAndStoreMemberPhoto(access: String, done: () -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(gateway + "/account/photo").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Authorization", "Bearer $access")
                    setRequestProperty("Accept", "image/jpeg,image/png")
                }

                if (connection.responseCode in 200..299) {
                    val temp = File(filesDir, "member_photo.tmp")
                    connection.inputStream.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    val target = memberPhotoFile()
                    if (temp.length() > 0L) {
                        temp.copyTo(target, overwrite = true)
                    }
                    temp.delete()
                }
            } catch (_: Exception) {
                // Keep the existing stored photo if the refresh fails.
            } finally {
                connection?.disconnect()
                runOnUiThread { done() }
            }
        }
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