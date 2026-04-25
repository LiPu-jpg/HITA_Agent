package com.limpu.hitax.ui.eas.login

import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.eas.EASToken

class LoginTrigger : Trigger() {
    lateinit var username: String
    lateinit var password: String
    lateinit var campus: EASToken.Campus
    var code: String? = null

    companion object {
        fun getActioning(
            username: String,
            password: String,
            campus: EASToken.Campus,
            code: String?
        ): LoginTrigger {
            val r = LoginTrigger()
            r.username = username
            r.password = password
            r.campus = campus
            r.code = code
            r.setActioning()
            return r
        }
    }
}