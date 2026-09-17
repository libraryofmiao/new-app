package `in`.miaolibrary.app

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Adds the first non-empty value found under the supplied JSON keys.
 * Kept as a top-level helper so MainActivity can use it without changing
 * the existing gateway or UI code.
 */
fun addFirstPresent(
    parent: LinearLayout,
    label: String,
    item: JSONObject,
    vararg keys: String
) {
    val value = keys.asSequence()
        .map { key -> item.optString(key, "").trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return

    val context = parent.context
    val muted = Color.rgb(105, 112, 117)
    parent.addView(TextView(context).apply {
        text = "$label: $value"
        textSize = 13f
        setTextColor(muted)
    })
}
