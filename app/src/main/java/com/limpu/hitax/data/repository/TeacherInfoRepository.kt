package com.limpu.hitax.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import javax.inject.Inject
import com.limpu.hitax.data.source.web.TeacherWebSource
import com.limpu.component.data.DataState
import com.limpu.hitax.ui.search.teacher.TeacherSearched

class TeacherInfoRepository @Inject constructor(application: Application) {


    fun getTeacherProfile(
        teacherId: String,
        teacherUrl: String
    ): LiveData<DataState<Map<String, String>>> {
        return TeacherWebSource.getTeacherProfile(teacherId,teacherUrl)
    }

    fun getTeacherPages(
        teacherId: String
    ): LiveData<DataState<Map<String, String>>> {
        return TeacherWebSource.getTeacherPages(teacherId)
    }


    fun searchTeachers(text:String):LiveData<DataState<List<TeacherSearched>>>{
        return TeacherWebSource.searchTeachers(text)
    }
}
