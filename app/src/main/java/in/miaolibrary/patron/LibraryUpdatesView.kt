package in.miaolibrary.patron

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Reusable Home-screen renderer for future CMS content. */
object LibraryUpdatesView {
    fun create(parentContext: android.content.Context, items: List<LibraryContentItem> = emptyList()): LinearLayout {
        val container = LinearLayout(parentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 8)
        }
        container.addView(TextView(parentContext).apply {
            text = "Library Updates"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 4)
        })
        container.addView(TextView(parentContext).apply {
            text = "Announcements, events, and library notices"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })
        if (items.isEmpty()) {
            container.addView(card(parentContext, "No library updates are available right now."))
        } else {
            items.sortedBy { it.sortOrder }.forEach { item ->
                val text = buildString {
                    append(item.title.ifBlank { "Library update" })
                    if (item.body.isNotBlank()) append("\n\n${item.body}")
                }
                container.addView(card(parentContext, text))
            }
        }
        return container
    }

    private fun card(context: android.content.Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.DKGRAY)
        setPadding(18, 16, 18, 16)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 22f
            setStroke(1, Color.rgb(225, 231, 238))
        }
        elevation = 3f
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 14) }
    }
}
