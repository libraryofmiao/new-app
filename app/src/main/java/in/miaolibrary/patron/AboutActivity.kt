package `in`.miaolibrary.patron

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Modern informational screen for app purpose, privacy, and support guidance. */
class AboutActivity : Activity() {
    private val dark = Color.rgb(31, 43, 35)
    private val muted = Color.rgb(101, 112, 105)
    private val green = Color.rgb(49, 92, 58)
    private val cream = Color.rgb(248, 247, 241)
    private val gold = Color.rgb(182, 122, 53)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
        }

        root.addView(header())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content())
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(20, 16, 20, 16)
        setBackgroundColor(Color.WHITE)

        addView(loadLogo().apply {
            contentDescription = "Miao Library logo"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(48, 48))

        addView(TextView(this@AboutActivity).apply {
            text = "Miao Library"
            textSize = 18f
            setTextColor(dark)
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(TextView(this@AboutActivity).apply {
            text = "BACK"
            textSize = 11f
            letterSpacing = .12f
            setTextColor(green)
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 12, 4, 12)
            setOnClickListener { finish() }
        })
    }

    private fun content(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 24, 20, 28)

        addView(TextView(this@AboutActivity).apply {
            text = "About & Privacy"
            textSize = 30f
            setTextColor(dark)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        addView(TextView(this@AboutActivity).apply {
            text = "Miao Library · New Age Learning Centre"
            textSize = 13f
            setTextColor(gold)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = .04f
            setPadding(0, 0, 0, 22)
        })

        addSection(this, "About Miao Library", "This app gives patrons secure access to their library account, currently issued books, issue history, and the library catalogue through the library gateway.")
        addSection(this, "Privacy", "Your session is stored locally using Android Keystore encryption. Requests are sent through the configured secure library gateway. Account information is shown only to provide the library features available in the app.")
        addSection(this, "Catalogue & Loans", "The catalogue is read-only in this app. Book details, holdings, availability, current loans, and issue history are retrieved from the library system without changing catalogue or patron records.")
        addSection(this, "Library Updates", "Announcements, events, advertisements, images, and other published library content may appear on the Home screen and are supplied by the Library CMS.")
        addSection(this, "Support", "For account, loan, renewal, or notification questions, please contact Miao Library through its official library channels.")

        addView(TextView(this@AboutActivity).apply {
            text = "MIAO LIBRARY"
            textSize = 11f
            setTextColor(muted)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = .18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 4)
        })
        addView(TextView(this@AboutActivity).apply {
            text = "Secure patron access"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
    }

    private fun addSection(parent: LinearLayout, title: String, body: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f
                setStroke(1, Color.rgb(225, 228, 221))
            }
            elevation = 1f
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(dark)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 7)
        })
        card.addView(TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(2f, 1.05f)
        })
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12
        })
    }

    private fun loadLogo(): ImageView {
        val image = ImageView(this)
        runCatching {
            val encoded = assets.open("miao_logo_base64.txt").bufferedReader().use { it.readText().trim() }
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            image.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        return image
    }
}
