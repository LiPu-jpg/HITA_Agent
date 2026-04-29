package com.limpu.hitax.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.ui.event.FragmentTimeInfoSheet
import java.util.*

object EventsUtils {
    fun showEventItem(context: Context, eventItem: EventItem) {
        android.util.Log.d("EventsUtils", "🚀 showEventItem called for: ${eventItem.name}")
        val activity = context as? AppCompatActivity
        if (activity == null) {
            android.util.Log.e("EventsUtils", "❌ Context is not AppCompatActivity: ${context.javaClass.name}")
            return
        }
        android.util.Log.d("EventsUtils", "✅ Got activity: ${activity.javaClass.simpleName}")
        val list: ArrayList<EventItem> = ArrayList<EventItem>()
        list.add(eventItem)
        val sheet = FragmentTimeInfoSheet.newInstance(list)
        android.util.Log.d("EventsUtils", "📋 Showing bottom sheet...")
        sheet.show(activity.supportFragmentManager, "event")
        android.util.Log.d("EventsUtils", "✅ Bottom sheet shown")
    }

    fun showEventItem(context: Context, eventItems: List<EventItem>) {
        android.util.Log.d("EventsUtils", "🚀 showEventItem called for ${eventItems.size} events")
        val activity = context as? AppCompatActivity
        if (activity == null) {
            android.util.Log.e("EventsUtils", "❌ Context is not AppCompatActivity: ${context.javaClass.name}")
            return
        }
        val list: ArrayList<EventItem> = ArrayList<EventItem>(eventItems)
        FragmentTimeInfoSheet.newInstance(list)
            .show(activity.supportFragmentManager, "event")
    }

    /**
     * 获得当前是第几节课
     * num为节数*10（+5）
     */
    fun getCurrentNumberText(context: Context,num:Int): String {
        val base = num / 10
        val plus = num % 10
        return if (base == 0) {
            context.getString(R.string.before_first_class)
        } else if (base == 12 && plus != 0) {
            context.getString(R.string.after_last_class)
        } else {
            if (plus == 0) context.getString(
                R.string.class_number_what,
                base
            ) else context.getString(R.string.class_after_number_what, base)
        }
    }

}