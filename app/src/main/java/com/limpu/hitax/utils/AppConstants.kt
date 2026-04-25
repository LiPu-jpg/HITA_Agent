package com.limpu.hitax.utils

/**
 * 应用常量定义
 * 统一管理应用中的常量，避免魔法数字和硬编码字符串
 */
object AppConstants {

    // ========== 应用信息 ==========
    const val APP_NAME = "HITA Agent"
    const val APP_PACKAGE = "com.limpu.hitax"

    // ========== 网络配置 ==========
    object Network {
        const val CONNECT_TIMEOUT = 30_000L // 30秒
        const val READ_TIMEOUT = 30_000L     // 30秒
        const val WRITE_TIMEOUT = 30_000L    // 30秒

        const val MAX_RETRY_COUNT = 3
        const val RETRY_DELAY_MS = 1_000L

        // MIME类型
        const val MIME_TYPE_JSON = "application/json"
        const val MIME_TYPE_TEXT = "text/plain"
        const val MIME_TYPE_HTML = "text/html"
        const val MIME_TYPE_FORM_DATA = "multipart/form-data"
    }

    // ========== 缓存配置 ==========
    object Cache {
        const val DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
        const val MEMORY_CACHE_SIZE = 20 * 1024 * 1024L // 20MB

        const val CACHE_MAX_AGE = 7 * 24 * 60 * 60 * 1000L // 7天
        const val CACHE_STALE_WHILE_REVALIDATE = 60 * 60 * 1000L // 1小时
    }

    // ========== UI配置 ==========
    object UI {
        // 动画时长
        const val ANIMATION_DURATION_SHORT = 150L
        const val ANIMATION_DURATION_MEDIUM = 300L
        const val ANIMATION_DURATION_LONG = 500L

        // 文本长度限制
        const val MAX_TEXT_LENGTH_SHORT = 50
        const val MAX_TEXT_LENGTH_MEDIUM = 200
        const val MAX_TEXT_LENGTH_LONG = 500

        // 列表配置
        const val LIST_ITEM_ANIMATION_DELAY = 50L
        const val LIST_PREFETCH_DISTANCE = 10
    }

    // ========== 数据库配置 ==========
    object Database {
        const val DATABASE_NAME = "hita_database"
        const val DATABASE_VERSION = 1

        const val TABLE_USERS = "users"
        const val TABLE_TIMETABLES = "timetables"
        const val TABLE_EVENTS = "events"
        const val TABLE_SUBJECTS = "subjects"
    }

    // ========== SharedPreferences ==========
    object Prefs {
        const val PREFS_NAME = "hita_prefs"
        const val PREFS_NAME_ENCRYPTED = "hita_encrypted_prefs"

        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "token"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }

    // ========== 文件配置 ==========
    object Files {
        const val TEMP_DIR = "temp"
        const val CACHE_DIR = "cache"
        const val DOWNLOAD_DIR = "downloads"

        const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100MB
        const val MAX_IMAGE_SIZE = 20 * 1024 * 1024L   // 20MB
        const val MAX_VIDEO_SIZE = 200 * 1024 * 1024L  // 200MB
    }

    // ========== Intent Extra Keys ==========
    object Extras {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_CAMPUS = "extra_campus"
    }

    // ========== Request Codes ==========
    object RequestCodes {
        const val REQUEST_CODE_PERMISSION = 1000
        const val REQUEST_CODE_PICK_FILE = 1001
        const val REQUEST_CODE_CAMERA = 1002
        const val REQUEST_CODE_LOGIN = 1003
    }

    // ========== 时间相关 ==========
    object Time {
        const val MILLIS_PER_SECOND = 1000L
        const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
        const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
        const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
        const val MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY

        const val SECONDS_PER_MINUTE = 60
        const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
        const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
    }

    // ========== 正则表达式 ==========
    object Regex {
        val EMAIL = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val PHONE_CN = Regex("^1[3-9]\\d{9}$")
        val URL = Regex("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$")
        val IP_ADDRESS = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
    }

    // ========== 错误消息 ==========
    object ErrorMessages {
        const val NETWORK_ERROR = "网络连接失败，请检查网络设置"
        const val SERVER_ERROR = "服务器错误，请稍后重试"
        const val TIMEOUT_ERROR = "请求超时，请稍后重试"
        const val UNKNOWN_ERROR = "发生未知错误，请稍后重试"
        const val PARSE_ERROR = "数据解析失败"
        const val AUTH_ERROR = "认证失败，请重新登录"
        const val PERMISSION_ERROR = "权限不足，请授予必要权限"
        const val FILE_ERROR = "文件操作失败"
        const val DATABASE_ERROR = "数据库操作失败"
    }

    // ========== 成功消息 ==========
    object SuccessMessages {
        const val SAVE_SUCCESS = "保存成功"
        const val DELETE_SUCCESS = "删除成功"
        const val UPDATE_SUCCESS = "更新成功"
        const val SYNC_SUCCESS = "同步成功"
        const val UPLOAD_SUCCESS = "上传成功"
        const val DOWNLOAD_SUCCESS = "下载成功"
        const val COPY_SUCCESS = "复制成功"
    }

    // ========== 状态定义 ==========
    object Status {
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val EMPTY = "empty"
        const val IDLE = "idle"
    }

    // ========== 分页配置 ==========
    object Pagination {
        const val PAGE_SIZE = 20
        const val PREFETCH_DISTANCE = 5
        const val INITIAL_PAGE = 1
    }

    // ========== 图像配置 ==========
    object Image {
        const val THUMBNAIL_SIZE = 100
        const val MEDIUM_SIZE = 400
        const val LARGE_SIZE = 800

        const val IMAGE_QUALITY = 85
        const val COMPRESSION_FORMAT = "JPEG"
    }

    // ========== 动画配置 ==========
    object Animation {
        const val FADE_IN_DURATION = 300L
        const val FADE_OUT_DURATION = 300L
        const val SLIDE_IN_DURATION = 350L
        const val SLIDE_OUT_DURATION = 350L
        const val SCALE_IN_DURATION = 200L
        const val SCALE_OUT_DURATION = 200L
    }

    // ========== 通知配置 ==========
    object Notification {
        const val CHANNEL_ID_GENERAL = "channel_general"
        const val CHANNEL_ID_DOWNLOAD = "channel_download"
        const val CHANNEL_ID_SYNC = "channel_sync"

        const val NOTIFICATION_ID_GENERAL = 1001
        const val NOTIFICATION_ID_DOWNLOAD = 1002
        const val NOTIFICATION_ID_SYNC = 1003
    }
}
