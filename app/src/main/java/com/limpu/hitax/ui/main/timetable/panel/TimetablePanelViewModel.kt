package com.limpu.hitax.ui.main.timetable.panel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.limpu.component.data.SharedPreferenceBooleanLiveData
import com.limpu.component.data.SharedPreferenceIntLiveData
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.EasSettingsRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.data.repository.TimetableStyleRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.data.repository.KEY_COLOR_ENABLE
import com.limpu.hitax.data.repository.KEY_DRAW_BG_LINE
import com.limpu.hitax.data.repository.KEY_FADE_ENABLE
import com.limpu.hitax.data.repository.KEY_LABEL_PERIOD
import com.limpu.hitax.data.repository.KEY_START_DATE
class TimetablePanelViewModel(application: Application) : AndroidViewModel(application) {

    private val timetableStyleRepository = TimetableStyleRepository(application)
    private val easSettingsRepository = EasSettingsRepository(application)
    private val subjectRepository = SubjectRepository(application)
    private val timetableRepository = TimetableRepository(application)

    val startDateLiveData: SharedPreferenceIntLiveData
        get() = timetableStyleRepository.startTimeLiveData
    val drawBGLinesLiveData: SharedPreferenceBooleanLiveData
        get() = timetableStyleRepository.drawBGLinesLiveData

    val colorEnableLiveData: SharedPreferenceBooleanLiveData
        get() = timetableStyleRepository.colorEnableLiveData

    val fadeEnableLiveData: SharedPreferenceBooleanLiveData
        get() = timetableStyleRepository.fadeEnableLiveData
    val periodLabelLiveData: SharedPreferenceBooleanLiveData
        get() = timetableStyleRepository.periodLabelLiveData
    val autoReimportLiveData: SharedPreferenceBooleanLiveData
        get() = easSettingsRepository.autoReimportLiveData


    fun changeStartDate(hour: Int, minute: Int) {
        val v = hour * 100 + minute
        timetableStyleRepository.putData(KEY_START_DATE,v)
    }
    fun setDrawBGLines(draw:Boolean) {
        timetableStyleRepository.putData(KEY_DRAW_BG_LINE,draw)
    }
    fun setColorEnable(draw:Boolean) {
        timetableStyleRepository.putData(KEY_COLOR_ENABLE,draw)
    }
    fun setFadeEnable(draw:Boolean) {
        timetableStyleRepository.putData(KEY_FADE_ENABLE,draw)
    }
    fun setPeriodLabelEnabled(enabled: Boolean) {
        timetableStyleRepository.putData(KEY_LABEL_PERIOD, enabled)
    }
    fun setAutoReimportEnabled(enabled: Boolean) {
        easSettingsRepository.setAutoReimport(enabled)
    }

    fun triggerAutoReimportNow() {
        val app: Application = getApplication()
        val easRepo = com.limpu.hitax.data.repository.EASRepository(
            app,
            EasPreferenceSource(app.applicationContext),
            TimetablePreferenceSource(app.applicationContext)
        )
        val token = easRepo.getEasToken()
        if (!token.isLogin()) return
        val isUndergrad = token.stutype == com.limpu.hitax.data.model.eas.EASToken.TYPE.UNDERGRAD
        easRepo.startAutoImportCurrentTimetable(isUndergrad) { success ->
            if (success) {
                easSettingsRepository.setLastAutoReimportTs(System.currentTimeMillis())
            }
        }
    }

    fun startResetColor(){
        subjectRepository.actionResetRecentSubjectColors()
    }
}
