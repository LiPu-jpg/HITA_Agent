package com.limpu.hitax.data.source.web.service

import androidx.lifecycle.LiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.eas.*
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.ui.eas.classroom.BuildingItem
import com.limpu.hitax.ui.eas.classroom.ClassroomItem
import java.util.*

interface AdditionalService {


    fun getLectures(
        pageSize:Int,
        pageOffset:Int
    ):LiveData<DataState<List<Map<String,String>>>>


    fun getNewsMeta(
        link:String
    ):LiveData<DataState<Map<String,String>>>

}