package com.limpu.hitax.data.model.eas

import java.util.*

class EASToken {

    enum class TYPE { UNDERGRAD, GRAD }

    enum class Campus {
        SHENZHEN,
        BENBU,
        WEIHAI
    }

    // --- 新 API (mjw.hitsz.edu.cn/incoSpringBoot) bearer token 认证 ---
    var accessToken: String? = null
    var refreshToken: String? = null
    // route / JSESSIONID 由 Jsoup session 自动管理，同时存一份供跨请求复用
    var cookies = HashMap<String, String>()

    var campus: Campus = Campus.SHENZHEN
    var username: String? = null
    var password: String? = null
    var name: String? = null
    var stutype: TYPE = TYPE.UNDERGRAD // 培养类型，1本科生，其他研究生
    var picture: String? = null //照片
    var id: String? = null //学生id
    var stuId: String? = null //学号
    var school: String? = null // 学院
    var major: String? = null //专业
    var grade: String? = null //年级
    var className: String? = null //班级
    var sfxsx: String? = null
    var email: String? = null //邮箱
    var phone: String? = null //电话

    fun getStudentType(): String {
        return if (stutype == TYPE.UNDERGRAD) "1" else "2"
    }

    fun isBenbuCampus(): Boolean {
        return campus == Campus.BENBU
    }

    fun isLogin(): Boolean {
        return !accessToken.isNullOrEmpty() || cookies.isNotEmpty()
    }

    override fun toString(): String {
        return "EASToken(campus=$campus, accessToken=${accessToken?.take(10)}..., username=$username, name=$name, stutype=${getStudentType()}, stuId=$stuId, school=$school)"
    }


}