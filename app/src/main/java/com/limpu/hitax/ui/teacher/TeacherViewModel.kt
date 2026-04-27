package com.limpu.hitax.ui.teacher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.repository.TeacherInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TeacherViewModel @Inject constructor(
    private val teacherInfoRepository: TeacherInfoRepository
) : ViewModel() {

    /**
     * 数据区
     */
    val teacherKeyLiveData = MutableLiveData<TeacherKey>()

    val teacherProfileLiveData: LiveData<DataState<Map<String, String>>> = teacherKeyLiveData.switchMap{
            return@switchMap teacherInfoRepository.getTeacherProfile(it.id, it.url)
        }

    val teacherPagesLiveData:LiveData<DataState<Map<String,String>>> = teacherKeyLiveData.switchMap{
        return@switchMap teacherInfoRepository.getTeacherPages(it.id)
    }


    /**
     * 方法
     */
    fun startRefresh(teacherId:String,teacherUrl:String,teacherName:String?){
        val teacherKey = TeacherKey()
        teacherKey.id = teacherId
        teacherKey.name = teacherName?:""
        teacherKey.url = teacherUrl
        teacherKeyLiveData.value = teacherKey
    }
}
