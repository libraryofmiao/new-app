package in.miaolibrary.patron

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val GATEWAY_URL = "https://api.miaolibrary.in"

data class Book(val title: String, val author: String, val dueDate: String, val callNumber: String, val status: String, val detailsUrl: String = "")
data class HistoryRecord(val title: String, val author: String, val date: String, val callNumber: String, val status: String)
data class CatalogueItem(val biblionumber: Int, val title: String, val author: String, val library: String, val callNumber: String, val availability: String, val holdingCount: Int)
data class Holding(val itemType: String, val currentLibrary: String, val homeLibrary: String, val collection: String, val shelvingLocation: String, val callNumber: String, val materialsSpecified: String, val volumeInfo: String, val copyNumber: String, val status: String, val notes: String, val dateDue: String, val barcode: String)
data class BookDetails(val biblionumber: Int, val title: String, val author: String, val holdings: List<Holding>)
data class AccountInfo(val username: String, val cardNumber: String, val firstName: String, val surname: String, val email: String, val phone: String, val address: String)

class LibraryApi {
    fun login(username: String, password: String): Result<String> {
        val body = JSONObject().put("username", username).put("password", password).toString()
        return request("/login", null, "POST", body).map { json ->
            json.optString("access_token").takeIf { it.isNotBlank() }
                ?: throw Exception("Login response did not contain an access token")
        }
    }

    fun account(token: String): Result<AccountInfo> = request("/account", token, "GET", null).map { json ->
        AccountInfo(json.optString("username"), json.optString("cardnumber", json.optString("card_number")), json.optString("firstname", json.optString("first_name")), json.optString("surname", json.optString("last_name")), json.optString("email"), json.optString("phone", json.optString("mobile")), json.optString("address", json.optString("address1")))
    }

    fun myBooks(token: String): Result<List<Book>> = request("/my-books", token, "GET", null).map { json ->
        val array = json.optJSONArray("books") ?: return@map emptyList()
        buildList { for (index in 0 until array.length()) { val item = array.optJSONObject(index) ?: continue; add(Book(item.optString("title", "Untitled"), item.optString("author"), item.optString("due_date"), item.optString("call_number"), item.optString("status", "checked_out"), item.optString("details_url"))) } }
    }

    fun issueHistory(token: String): Result<List<HistoryRecord>> = request("/issue-history", token, "GET", null).map { json ->
        val array = json.optJSONArray("records") ?: return@map emptyList()
        buildList { for (index in 0 until array.length()) { val item = array.optJSONObject(index) ?: continue; add(HistoryRecord(item.optString("title", "Untitled"), item.optString("author"), item.optString("date"), item.optString("call_number"), item.optString("status"))) } }
    }

    fun catalogueSearch(token: String, query: String): Result<List<CatalogueItem>> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return request("/catalogue/search?q=$encoded", token, "GET", null).map { json ->
            val array = json.optJSONArray("results") ?: return@map emptyList()
            buildList { for (index in 0 until array.length()) { val item = array.optJSONObject(index) ?: continue; add(CatalogueItem(item.optInt("biblionumber"), item.optString("title", "Untitled"), item.optString("author"), item.optString("library"), item.optString("call_number"), item.optString("availability"), item.optInt("holding_count"))) } }
        }
    }

    fun bookDetails(token: String, biblionumber: Int): Result<BookDetails> = request("/book-details/$biblionumber", token, "GET", null).map { json ->
        val array = json.optJSONArray("holdings") ?: org.json.JSONArray()
        val holdings = buildList { for (index in 0 until array.length()) { val item = array.optJSONObject(index) ?: continue; add(Holding(item.optString("item_type"), item.optString("current_library"), item.optString("home_library"), item.optString("collection"), item.optString("shelving_location"), item.optString("call_number"), item.optString("materials_specified"), item.optString("volume_info"), item.optString("copy_number"), item.optString("status"), item.optString("notes"), item.optString("date_due"), item.optString("barcode"))) } }
        BookDetails(json.optInt("biblionumber", biblionumber), json.optString("title", "Untitled"), json.optString("author"), holdings)
    }

    /** Reads published announcements, events, and advertisements from the future gateway CMS feed. */
    fun libraryContent(): Result<List<LibraryContentItem>> = request("/cms/content", null, "GET", null).map { json ->
        LibraryContentParser.parse(json)
    }

    private fun request(path: String, token: String?, method: String, body: String?): Result<JSONObject> = try {
        val connection = (URL(GATEWAY_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (code in 200..299 && json != null) Result.success(json)
        else Result.failure(Exception(json?.optString("detail").orEmpty().ifBlank { "Server error ($code)" }))
    } catch (error: Exception) { Result.failure(error) }
}
