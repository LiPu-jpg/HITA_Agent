package com.limpu.hitax.ui.widgets.today

import android.content.Intent
import android.widget.RemoteViewsService
import com.limpu.hitax.ui.widgets.today.normal.ListRemoteViewsFactory
import com.limpu.hitax.ui.widgets.today.slim.ListRemoteViewsSlimFactory

class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val slim = intent.getBooleanExtra("slim", false)
        return if(slim) ListRemoteViewsSlimFactory(this, intent)
        else ListRemoteViewsFactory(this, intent)
    }
}