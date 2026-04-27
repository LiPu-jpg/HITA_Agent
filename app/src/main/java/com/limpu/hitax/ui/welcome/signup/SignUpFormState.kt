package com.limpu.hitax.ui.welcome.signup

/**
 * 注册表单View的数据封装
 */
class SignUpFormState {
    var usernameError //用户名错误
            : Int? = null
        private set
    var passwordError //密码错误
            : Int? = null
        private set
    var passwordConfirmError //确认密码错误
            : Int? = null
        private set
    var nicknameError //昵称错误
            : Int? = null
        private set
    var agreementError //协议未勾选错误
            : Int? = null
        private set
    var isFormValid = false
        private set

    constructor(usernameError: Int?, passwordError: Int?, passwordConfirmError: Int?, nicknameError: Int?, agreementError: Int? = null) {
        this.usernameError = usernameError
        this.passwordError = passwordError
        this.passwordConfirmError = passwordConfirmError
        this.nicknameError = nicknameError
        this.agreementError = agreementError
    }

    constructor(formValid: Boolean) {
        if (formValid) {
            usernameError = null
            passwordError = null
            passwordConfirmError = null
            nicknameError = null
            agreementError = null
        }
        isFormValid = formValid
    }

}
