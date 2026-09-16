package in.miaolibrary.patron

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GATEWAY_URL = "https://api.miaolibrary.in"

data class Book(val title: String, val author: String, val dueDate: String, val callNumber: String, val status: String)

class LibraryApi {
    fun login(username: String, password: String): Result<String> {
        val body = JSONObject().put("username", username).put("password", password).toString()
        return request("/login", null, "POST", body).map { json ->
            json.optString("access_token").takeIf { it.isNotBlank() }
                ?: throw Exception("Login response did not contain an access token")
        }
    }

    fun myBooks(token: String): Result<List<Book>> = request("/my-books", token, "GET", null).map { json ->
        val array = json.optJSONArray("books") ?: return@map emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Book(item.optString("title", "Untitled"), item.optString("author"), item.optString("due_date"), item.optString("call_number"), item.optString("status", "checked_out")))
            }
        }
    }

    private fun request(path: String, token: String?, method: String, body: String?): Result<JSONObject> = try {
        val connection = (URL(GATEWAY_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (code in 200..299 && json != null) Result.success(json)
        else Result.failure(Exception("HTTP $code: " + (json?.optString("detail").orEmpty().ifBlank { "Server error" })))
    } catch (error: Exception) {
        Result.failure(error)
    }
}
