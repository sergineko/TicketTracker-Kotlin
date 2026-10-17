package es.sergiotech.tickettracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import es.sergiotech.tickettracker.MainActivity
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.data.LocalPreferences
import es.sergiotech.tickettracker.domain.PurchaseRepository
import es.sergiotech.tickettracker.domain.upcomingDeadlines
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDependencies {
    fun repository(): PurchaseRepository

    fun preferences(): LocalPreferences
}

class DeadlineReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies =
            EntryPointAccessors.fromApplication(
                applicationContext,
                ReminderDependencies::class.java,
            )
        return try {
            if (!dependencies.preferences().preferences.first().reminders) return Result.success()
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            )
                return Result.success()
            val today = LocalDate.now()
            val history =
                applicationContext.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
            if (history.getString("last_day", null) == today.toString()) return Result.success()
            val alerts =
                upcomingDeadlines(dependencies.repository().observePurchases().first(), today)
            if (alerts.isNotEmpty()) {
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        "deadlines",
                        applicationContext.getString(R.string.notification_channel),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
                val action =
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        Intent(applicationContext, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                manager.notify(
                    100,
                    NotificationCompat.Builder(applicationContext, "deadlines")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(applicationContext.getString(R.string.notification_title))
                        .setContentText(applicationContext.getString(R.string.notification_body))
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setContentIntent(action)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .build(),
                )
                history.edit().putString("last_day", today.toString()).apply()
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (enabled)
                manager.enqueueUniquePeriodicWork(
                    "deadline-reminders",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<DeadlineReminderWorker>(24, TimeUnit.HOURS)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .build(),
                )
            else manager.cancelUniqueWork("deadline-reminders")
        }
    }
}
