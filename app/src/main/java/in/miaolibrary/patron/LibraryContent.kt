package in.miaolibrary.patron

import org.json.JSONArray
import org.json.JSONObject

data class LibraryContentItem(
    val id: String,
    val type: ContentType,
    val title: String,
    val body: String,
    val imageUrl: String,
    val html: String,
    val publishedAt: String,
    val expiresAt: String,
    val sortOrder: Int,
    val notificationEnabled: Boolean,
    val logoUrl: String
)

enum class ContentType {
    ANNOUNCEMENT,
    EVENT,
    ADVERTISEMENT,
    UNKNOWN
}

object LibraryContentParser {
    fun parse(response: JSONObject): List<LibraryContentItem> {
        val items = response.optJSONArray("items")
            ?: response.optJSONArray("content")
            ?: JSONArray()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = when (item.optString("type").lowercase()) {
                    "announcement", "notice", "notification" -> ContentType.ANNOUNCEMENT
                    "event" -> ContentType.EVENT
                    "advertisement", "ad", "banner" -> ContentType.ADVERTISEMENT
                    else -> ContentType.UNKNOWN
                }
                add(
                    LibraryContentItem(
                        id = item.optString("id", item.optString("slug", index.toString())),
                        type = type,
                        title = item.optString("title", "Library update"),
                        body = item.optString("body", item.optString("description")),
                        imageUrl = item.optString("image_url", item.optString("image")),
                        html = item.optString("html"),
                        publishedAt = item.optString("published_at", item.optString("start_at")),
                        expiresAt = item.optString("expires_at", item.optString("end_at")),
                        sortOrder = item.optInt("sort_order", index),
                        notificationEnabled = item.optBoolean("notification_enabled", false),
                        logoUrl = item.optString("logo_url", item.optString("logo"))
                    )
                )
            }
        }.sortedBy { it.sortOrder }
    }
}
