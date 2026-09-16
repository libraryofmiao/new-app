package `in`.miaolibrary.patron

import android.graphics.Color
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

/** Renders the patron-facing CMS feed. */
object LibraryUpdatesView {
    fun create(context: android.content.Context, items: List<LibraryContentItem>): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 8)
        }
        container.addView(TextView(context).apply {
            text = "Library Updates"
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })
        container.addView(TextView(context).apply {
            text = "Announcements, events, and library notices"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })

        if (items.isEmpty()) {
            container.addView(card(context, null, "No library updates are available right now.", null))
        } else {
            items.sortedBy { it.sortOrder }.forEach { item ->
                container.addView(card(context, item.title.ifBlank { "Library update" }, item.body, item))
            }
        }
        return container
    }

    private fun card(context: android.content.Context, title: String?, fallbackBody: String, item: LibraryContentItem?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 16, 18, 16)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 22f
            setStroke(1, Color.rgb(225, 231, 238))
        }
        elevation = 3f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 14)
        }

        if (item != null) {
            addView(TextView(context).apply {
                text = item.type.name.lowercase().replace('_', ' ')
                textSize = 12f
                setTextColor(Color.rgb(180, 122, 53))
                setPadding(0, 0, 0, 5)
            })
        }
        if (!title.isNullOrBlank()) {
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            })
        }
        item?.logoUrl?.takeIf { it.startsWith("https://") }?.let { url ->
            addView(ImageView(context).apply {
                adjustViewBounds = true
                contentDescription = "Library logo"
                loadImageAsync(url, this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110).apply { bottomMargin = 8 }
            })
        }
        item?.imageUrl?.takeIf { it.startsWith("https://") }?.let { url ->
            addView(ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = title ?: "Library update image"
                loadImageAsync(url, this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220).apply { bottomMargin = 10 }
            })
        }

        val html = item?.html?.takeIf { it.isNotBlank() }
        val text = if (html != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT) else Html.fromHtml(html)
        } else fallbackBody
        addView(TextView(context).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.DKGRAY)
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
            }.getOrNull()?.let { bitmap ->
                target.post { target.setImageBitmap(bitmap) }
            }
        }.start()
    }
}
