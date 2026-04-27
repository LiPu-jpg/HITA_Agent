package com.limpu.hitax.data.source.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

private const val SP_NAME_BENBU_START_DATE = "benbu_start_date"

private fun startDateKey(termCode: String) = "start_date_$termCode"

private fun confirmKey(termCode: String) = "confirmed_$termCode"

class BenbuStartDatePreferenceSource constructor(context: Context) {
    private val preference: SharedPreferences =
        context.applicationContext.getSharedPreferences(SP_NAME_BENBU_START_DATE, Context.MODE_PRIVATE)

    fun getStartDateMillis(termCode: String): Long? {
        val key = startDateKey(termCode)
        return if (preference.contains(key)) preference.getLong(key, 0L) else null
    }

    fun isConfirmed(termCode: String): Boolean {
        return preference.getBoolean(confirmKey(termCode), false)
    }

    fun saveCalibration(termCode: String, startDateMillis: Long, confirmed: Boolean = true) {
        preference.edit()
            .putLong(startDateKey(termCode), startDateMillis)
            .putBoolean(confirmKey(termCode), confirmed)
            .apply()
    }

    fun clearCalibration(termCode: String) {
        preference.edit()
            .remove(startDateKey(termCode))
            .remove(confirmKey(termCode))
            .apply()
    }
}
