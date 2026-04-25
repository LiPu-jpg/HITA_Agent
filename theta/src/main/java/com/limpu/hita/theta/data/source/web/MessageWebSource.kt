package com.limpu.hita.theta.data.source.web

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.limpu.component.data.DataState
import com.limpu.component.web.BaseWebSource
import com.limpu.hita.theta.data.model.Message
import com.limpu.hita.theta.data.source.web.service.MessageService
import com.limpu.stupiduser.data.source.web.service.codes
import com.limpu.stupiduser.data.source.web.service.codes.SUCCESS
import com.limpu.stupiduser.util.HttpUtils

class MessageWebSource(context: Context) : BaseWebSource<MessageService>(
    context
) {
    override fun getServiceClass(): Class<MessageService> {
        return MessageService::class.java
    }

    fun getMessages(
        token: String,
        mode: String,
        pageSize: Int,
        pageNum: Int
    ): LiveData<DataState<List<Message>>> {
        return service.getMessages(
                HttpUtils.getHeaderAuth(token), mode, pageSize, pageNum
            ).map{ input ->
            if (input != null) {
                when (input.code) {
                    SUCCESS -> return@map DataState(input.data ?: listOf())
                    codes.TOKEN_INVALID -> return@map DataState(DataState.STATE.TOKEN_INVALID)
                    else -> return@map DataState(
                        DataState.STATE.FETCH_FAILED,
                        input.message
                    )
                }
            }
            DataState(DataState.STATE.FETCH_FAILED)
        }
    }

    fun countUnread(
        token: String,
        mode: String
    ): LiveData<DataState<Int>> {
        return service.countUnread(
                HttpUtils.getHeaderAuth(token), mode
            ).map { input ->
            if (input != null) {
                when (input.code) {
                    SUCCESS -> return@map DataState(input.data ?: 0)
                    codes.TOKEN_INVALID -> return@map DataState(DataState.STATE.TOKEN_INVALID)
                    else -> return@map DataState(
                        DataState.STATE.FETCH_FAILED,
                        input.message
                    )
                }
            }
            DataState(DataState.STATE.FETCH_FAILED)
        }
    }


    companion object {
        var instance: MessageWebSource? = null
        fun getInstance(context: Context): MessageWebSource {
            synchronized(MessageWebSource::class.java) {
                if (instance == null) {
                    instance = MessageWebSource(context.applicationContext)
                }
                return instance!!
            }
        }
    }

}