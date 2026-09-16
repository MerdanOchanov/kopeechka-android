package app.kopeechka.finance.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kopeechka.finance.KopeechkaApp
import app.kopeechka.finance.MainActivity
import app.kopeechka.finance.R
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.net.DriveBackup
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Schedules {
    private const val BACKUP = "auto-backup"
    private const val REMIND = "evening-remind"
    const val CHANNEL = "remind"

    fun syncAutoBackup(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(BACKUP)
            return
        }
        val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        wm.enqueueUniquePeriodicWork(BACKUP, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun syncReminder(ctx: Context, enabled: Boolean, hour: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(REMIND)
            return
        }
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(REMIND, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /**
     * Уведомление о новом черновике из СМС. Без него смысл функции теряется:
     * человек узнает о списании, только когда сам откроет приложение.
     */
    fun notifyDraft(ctx: Context, l: Lang, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx, l)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(l.t("sms.notifyTitle"))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(22, n) }
    }

    /** Канал уведомлений называется на языке интерфейса. */
    fun ensureChannel(ctx: Context, l: Lang = Lang.fromSystem()) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, l.t("notify.channel"), NotificationManager.IMPORTANCE_DEFAULT))
    }
}

/** Ежедневная копия в Google Диск — работает, только если доступ уже выдан (без экрана согласия). */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KopeechkaApp
        val s = app.store.current.settings
        if (!s.autoBackup || !s.driveLinked) return Result.success()
        return try {
            val auth = DriveBackup.authorize(applicationContext)
            val token = auth.accessToken
            if (auth.hasResolution() || token == null) return Result.success()
            DriveBackup.upload(token, app.store.exportJson())
            DriveBackup.prune(token, 10)
            app.store.update { it.copy(settings = it.settings.copy(lastBackupAt = System.currentTimeMillis())) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val l = Lang.of((ctx as KopeechkaApp).store.current.settings.lang)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        Schedules.ensureChannel(ctx, l)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Schedules.CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(l.t("app.name"))
            .setContentText(l.t("notify.text"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(21, n) }
        return Result.success()
    }
}
