package `in`.miaolibrary.patron

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/** Application-level notification setup for due-date reminders. */
class MiaoLibraryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DUE_DATE_NOTIFICATION_CHANNEL,
                "Due-date reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for books due today"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DUE_DATE_NOTIFICATION_CHANNEL = "due_date_reminders"
    }
}
