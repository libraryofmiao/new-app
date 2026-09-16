package `in`.miaolibrary.patron

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

/** Patron-facing CMS cards styled to match the modern Miao Library UI. */
object LibraryUpdatesView {
    private val dark = Color.rgb(31, 43, 35)
    private val muted = Color.rgb(101, 112, 105)
    private val green = Color.rgb(49, 92, 58)
    private val gold = Color.rgb(182, 122, 53)
    private val border = Color.rgb(225, 228, 221)

    fun create(context: android.content.Context, items: List<LibraryContentItem>): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }
        if (items.isEmpty()) {
            container.addView(card(context, "No library updates are available right now.", null))
        } else {
            items.sortedBy { it.sortOrder }.forEach { item ->
                container.addView(card(context, item.body.ifBlank { "Library update" }, item))
            }
        }
        return container
    }

    private fun card(context: android.content.Context, fallbackBody: String, item: LibraryContentItem?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(17, 15, 17, 15)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 18f
            setStroke(1, border)
        }
        elevation = 1f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 10
        }

        if (item != null) {
            addView(TextView(context).apply {
                text = when (item.type) {
                    ContentType.EVENT -> "EVENT"
                    ContentType.ADVERTISEMENT -> "LIBRARY NOTICE"
                    ContentType.ANNOUNCEMENT -> "ANNOUNCEMENT"
                    ContentType.UNKNOWN -> "LIBRARY UPDATE"
                }
                textSize = 10f
                setTextColor(gold)
                setTypeface(null, Typeface.BOLD)
                letterSpacing = .12f
                setPadding(0, 0, 0, 6)
            })
            if (item.title.isNotBlank()) addView(TextView(context).apply {
                text = item.title
                textSize = 18f
                setTextColor(dark)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            })
        }

        item?.logoUrl?.takeIf { it.startsWith("https://") }?.let { url ->
            addView(ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Library logo"
                loadImageAsync(url, this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 84).apply { bottomMargin = 8 }
            })
        }
        item?.imageUrl?.takeIf { it.startsWith("https://") }?.let { url ->
            addView(ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = item.title.ifBlank { "Library update image" }
                loadImageAsync(url, this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 210).apply { bottomMargin = 10 }
            })
        }

        val html = item?.html?.takeIf { it.isNotBlank() }
        val rendered = if (html != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT) else Html.fromHtml(html)
        } else fallbackBody
        addView(TextView(context).apply {
            text = rendered
            textSize = 14f
            setTextColor(muted)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, 0, 0, 2)
        })
    }

    private fun loadImageAsync(url: String, target: ImageView) {
        Thread {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { input -> android.graphics.BitmapFactory.decodeStream(input) }
            }.getOrNull()?.let { bitmap -> target.post { target.setImageBitmap(bitmap) } }
        }.start()
    }
}
