package `in`.miaolibrary.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

object IssuedBooksCache {
    private const val PREFS = "session"
    private const val CACHE = "issued_books_cache"
    private const val DAILY_FETCH_DATE = "issued_books_daily_fetch_date"
    private const val REQUEST_CODE = 845217
    private const val GATEWAY = "https://api.miaolibrary.in"

    fun cached(context: Context): JSONArray? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE, null) ?: return null
        return extractBooks(raw)
    }

    fun hasCache(context: Context): Boolean = cached(context) != null

    fun shouldDailyFetch(context: Context): Boolean {
        val now = Calendar.getInstance().apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
        if (now.get(Calendar.HOUR_OF_DAY) < 17) return false
        val today = dateKey(now)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(DAILY_FETCH_DATE, "") != today
    }

    fun fetch(
        context: Context,
        markDailyFetch: Boolean,
        callback: (Boolean, JSONArray) -> Unit
    ) {
        val access = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("access_token", null)

        if (access.isNullOrBlank()) {
            callback(false, cached(context) ?: JSONArray())
            return
        }

        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(GATEWAY + "/my-books").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $access")
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    callback(false, cached(context) ?: JSONArray())
                    return@thread
                }

                val books = extractBooks(response)

                // Replace the local issued-book record only after a successful
                // gateway fetch. The previous record remains untouched on failure.
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CACHE, response)
                    .apply()

                if (markDailyFetch) {
                    val now = Calendar.getInstance().apply {
                        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    }
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(DAILY_FETCH_DATE, dateKey(now))
                        .apply()
                }

                callback(true, books)
            } catch (_: Exception) {
                callback(false, cached(context) ?: JSONArray())
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, IssuedBooksDailyRefreshReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance().apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
        val trigger = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.timeInMillis,
            pending
        )
    }

    private fun dateKey(calendar: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

    private fun extractBooks(raw: String): JSONArray {
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) return JSONArray(trimmed)

            val root = JSONObject(trimmed)
            val preferred = listOf("books", "items", "issues", "current", "current_books")

            for (key in preferred) {
                val value = root.opt(key)
                if (value is JSONArray) return value
            }

            findArray(root)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun findArray(obj: JSONObject): JSONArray {
        val preferred = listOf("books", "items", "issues", "current", "current_books", "data")
        for (key in preferred) {
            val value = obj.opt(key)
            if (value is JSONArray) return value
            if (value is JSONObject) {
                val nested = findArray(value)
                if (nested.length() > 0) return nested
            }
        }

        val names = obj.keys()
        while (names.hasNext()) {
            val value = obj.opt(names.next())
            if (value is JSONObject) {
                val nested = findArray(value)
                if (nested.length() > 0) return nested
            }
        }
        return JSONArray()
    }
}

class IssuedBooksDailyRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            IssuedBooksCache.scheduleDaily(context)
            return
        }

        val pending = goAsync()
        IssuedBooksCache.fetch(context, true) { ok, books ->
            if (ok) {
                DueDateNotificationScheduler.sync(context, books)
            }
            IssuedBooksCache.scheduleDaily(context)
            pending.finish()
        }
    }
}
