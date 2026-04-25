package com.limpu.hitax.data.work

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.limpu.hitax.R
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.data.source.preference.CourseReminderStore
import com.limpu.hitax.ui.main.MainActivity
import com.limpu.hitax.utils.TimeTools
import java.util.Calendar

/**
 * 课程提醒 Worker
 * 每15分钟检查一次是否有即将开始的课程
 */
class CourseReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        val store = CourseReminderStore.getInstance(applicationContext)
        if (!store.isEnabled()) return Result.success()

        val minutes = store.getReminderMinutes()
        val app = applicationContext as? Application ?: return Result.success()
        val timetableRepo = TimetableRepository.getInstance(app)

        val now = System.currentTimeMillis()
        val windowEnd = now + minutes * 60 * 1000

        // 获取今天的事件
        val todayEvents = timetableRepo.getTodayEventsSync()
        val upcomingEvents = todayEvents.filter { event ->
            // 找到在提醒窗口内且尚未开始的课程
            event.from.time in (now + 1) until windowEnd
        }

        // 获取已发送提醒的课程 ID（使用 SharedPreferences 简单存储）
        val sentReminders = getSentReminders()
        val newEvents = upcomingEvents.filter { it.id !in sentReminders }

        if (newEvents.isNotEmpty()) {
            newEvents.forEach { sendNotification(it) }
            // 记录已发送提醒
            saveSentReminders(sentReminders + newEvents.map { it.id })
        }

        return Result.success()
    }

    private fun sendNotification(event: com.limpu.hitax.data.model.timetable.EventItem) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val store = CourseReminderStore.getInstance(applicationContext)
        val minutes = store.getReminderMinutes()

        val title = "⏰ 即将上课"
        val content = buildString {
            append("${event.name}")
            if (!event.place.isNullOrBlank()) {
                append(" @ ${event.place}")
            }
            append(" 将在 ${minutes}分钟后开始")
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_today)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(event.id.hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "课程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在课程开始前提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun getSentReminders(): Set<String> {
        val prefs = applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val today = TimeTools.getDateString(Calendar.getInstance())
        val savedDate = prefs.getString(KEY_DATE, "")
        
        // 如果是新的一天，清空记录
        return if (savedDate != today) {
            emptySet()
        } else {
            prefs.getStringSet(KEY_SENT_IDS, emptySet()) ?: emptySet()
        }
    }

    private fun saveSentReminders(ids: Set<String>) {
        val prefs = applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val today = TimeTools.getDateString(Calendar.getInstance())
        prefs.edit()
            .putStringSet(KEY_SENT_IDS, ids)
            .putString(KEY_DATE, today)
            .apply()
    }

    companion object {
        private const val CHANNEL_ID = "course_reminder"
        private const val SP_NAME = "course_reminder_sent"
        private const val KEY_SENT_IDS = "sent_ids"
        private const val KEY_DATE = "date"
    }
}
