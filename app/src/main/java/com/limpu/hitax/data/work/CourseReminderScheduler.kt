package com.limpu.hitax.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 课程提醒调度器
 */
object CourseReminderScheduler {
    private const val UNIQUE_WORK_NAME = "course_reminder"

    /**
     * 启动课程提醒（每15分钟检查一次）
     */
    fun schedule(context: Context) {
        // 15分钟是 WorkManager 的最小间隔
        val request = PeriodicWorkRequestBuilder<CourseReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 取消课程提醒
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * 根据开关状态自动调度或取消
     */
    fun autoSchedule(context: Context) {
        val store = com.limpu.hitax.data.source.preference.CourseReminderStore.getInstance(context)
        if (store.isEnabled()) {
            schedule(context)
        } else {
            cancel(context)
        }
    }
}
