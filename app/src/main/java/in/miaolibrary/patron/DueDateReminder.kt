package in.miaolibrary.patron

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DueDateReminderScheduler {
    private const val REQUEST_BASE = 41000

    fun schedule(context: Context, books: List<Book>) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        books.forEachIndexed { index, book ->
            val due = parseDate(book.dueDate) ?: return@forEachIndexed
            val trigger = Calendar.getInstance().apply {
                time = due
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (trigger.timeInMillis <= System.currentTimeMillis()) return@forEachIndexed
            val intent = Intent(context, DueDateReminderReceiver::class.java).apply {
                putExtra("title", book.title)
                putExtra("due_date", book.dueDate)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
            }
        }
    }

    private fun parseDate(value: String): Date? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            runCatching {
                return SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean)
            }
        }
        return null
    }
}

class DueDateReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Library book" }
        val dueDate = intent.getStringExtra("due_date").orEmpty()
        val message = if (dueDate.isBlank()) "This book is due today." else "This book is due today ($dueDate)."
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, MiaoLibraryApplication.DUE_DATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Book due today")
            .setContentText("$title — $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$message"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify((title.hashCode() and 0x7fffffff), notification)
    }
}
