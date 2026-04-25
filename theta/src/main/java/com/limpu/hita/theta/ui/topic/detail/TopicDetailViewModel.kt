package com.limpu.hita.theta.ui.topic.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hita.theta.data.repository.TopicRepository
import com.limpu.stupiduser.data.repository.LocalUserRepository

class TopicDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val topicRepo = TopicRepository.getInstance(application)
    private val userRepo = LocalUserRepository.getInstance(application)

    private val topicIdLiveData = MutableLiveData<String>()

    val topicLiveData = topicIdLiveData.switchMap {
        val user = userRepo.getLoggedInUser()
        if (user.isValid()) {
            return@switchMap topicRepo.getTopic(user.token!!, it)
        } else {
            return@switchMap MutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
        }
    }

    fun startRefresh(topicId: String) {
        topicIdLiveData.value = topicId
    }



}