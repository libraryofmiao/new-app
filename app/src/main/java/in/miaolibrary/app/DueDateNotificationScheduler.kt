package `in`.miaolibrary.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DueDateNotificationScheduler {
    private const val PREFS = "session"
    private const val REMINDERS = "due_date_reminders"
    private const val CHANNEL_ID = "due_date_reminders"
    private const val REQUEST_BASE = 730000

    fun sync(context: Context, books: JSONArray) {
        cancelAll(context)

        val stored = JSONArray()
        for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            val dueRaw = first(book, "date_due", "due_date", "duedate", "due") ?: continue
            val due = parseDueDate(dueRaw) ?: continue

            val title = first(book, "title", "name")?.ifBlank { "Your borrowed book" }
                ?: "Your borrowed book"
            val identity = first(
                book,
                "issue_id", "issueid", "issueId",
                "checkout_id", "checkoutId",
                "loan_id", "loanId",
                "barcode", "item_barcode"
            ) ?: "book_$i"

            val requestCode = REQUEST_BASE + stableId(identity)
            val triggerAt = triggerTime(due)

            if (triggerAt <= System.currentTimeMillis()) continue

            val reminder = JSONObject()
                .put("request_code", requestCode)
                .put("title", title)
                .put("due_date", dueRaw)
                .put("trigger_at", triggerAt)
            stored.put(reminder)

            schedule(context, requestCode, title, dueRaw, triggerAt)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(REMINDERS, stored.toString())
            .apply()
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(REMINDERS, null)
        if (!raw.isNullOrBlank()) {
            try {
                val reminders = JSONArray(raw)
                for (i in 0 until reminders.length()) {
                    val code = reminders.optJSONObject(i)?.optInt("request_code", -1) ?: -1
                    if (code >= 0) cancel(context, code)
                }
            } catch (_: Exception) {
            }
        }
        prefs.edit().remove(REMINDERS).apply()
    }

    fun restoreAfterBoot(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(REMINDERS, null) ?: return
        try {
            val reminders = JSONArray(raw)
            for (i in 0 until reminders.length()) {
                val item = reminders.optJSONObject(i) ?: continue
                val code = item.optInt("request_code", -1)
                val title = item.optString("title", "Your borrowed book")
                val due = item.optString("due_date", "")
                val triggerAt = item.optLong("trigger_at", 0L)
                if (code >= 0 && triggerAt > System.currentTimeMillis()) {
                    schedule(context, code, title, due, triggerAt)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun schedule(
        context: Context,
        requestCode: Int,
        title: String,
        dueDate: String,
        triggerAt: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DueDateNotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("due_date", dueDate)
            putExtra("notification_id", requestCode)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pending
        )
    }

    private fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DueDateNotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun triggerTime(dueDate: Calendar): Long {
        val trigger = dueDate.clone() as Calendar
        trigger.set(Calendar.HOUR_OF_DAY, 9)
        trigger.set(Calendar.MINUTE, 0)
        trigger.set(Calendar.SECOND, 0)
        trigger.set(Calendar.MILLISECOND, 0)

        if (trigger.timeInMillis <= System.currentTimeMillis()) {
            return System.currentTimeMillis() + 60_000L
        }
        return trigger.timeInMillis
    }

    private fun parseDueDate(value: String): Calendar? {
        val raw = value.trim()
        val formats = listOf(
            "yyyy-MM-dd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy",
            "MM/dd/yyyy"
        )

        for (format in formats) {
            try {
                val parser = SimpleDateFormat(format, Locale.US).apply {
                    isLenient = false
                }
                val date = parser.parse(raw) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun first(book: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = book.opt(key)
            if (value != null && value !is JSONObject && value !is JSONArray) {
                val text = value.toString().trim()
                if (text.isNotBlank() && text != "null") return text
            }
        }
        return null
    }

    private fun stableId(value: String): Int {
        return (value.hashCode() and 0x7fffffff) % 100000
    }
}

class DueDateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Your borrowed book"
        val dueDate = intent.getStringExtra("due_date").orEmpty()
        val notificationId = intent.getIntExtra("notification_id", title.hashCode())

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "due_date_reminders",
                    "Book due-date reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for books due today"
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            context,
            title.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (dueDate.isBlank()) {
            "Please return this book to the library today."
        } else {
            "Due today ($dueDate). Please return it to the library."
        }

        val notification = NotificationCompat.Builder(context, "due_date_reminders")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Book due today")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        manager.notify(notificationId, notification)
    }
}

class DueDateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            DueDateNotificationScheduler.restoreAfterBoot(context)
        }
    }
}
