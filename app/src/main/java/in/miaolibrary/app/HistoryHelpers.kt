package `in`.miaolibrary.app

import org.json.JSONObject

/** Returns true only when a circulation record clearly represents a returned/closed issue. */
fun isReturnedIssue(item: JSONObject): Boolean {
    val returnedKeys = listOf(
        "date_returned",
        "returned_date",
        "return_date",
        "checkin_date",
        "date_checkin"
    )
    if (returnedKeys.any {
            val value = item.optString(it, "").trim()
            value.isNotBlank() && value != "null"
        }) return true

    val status = firstHistoryValue(
        item,
        "status",
        "issue_status",
        "item_status",
        "loan_status"
    ).lowercase()

    return status.contains("return") ||
        status.contains("checkin") ||
        status.contains("closed")
}

private fun firstHistoryValue(item: JSONObject, vararg keys: String): String =
    keys.asSequence()
        .map { item.optString(it, "").trim() }
        .firstOrNull { it.isNotBlank() && it != "null" }
        ?: ""
