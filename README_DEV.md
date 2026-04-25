# HITA Agent 开发指南

## 🚀 快速开始

### 项目简介
HITA Agent 是一款基于 Android 的智能校园助手应用，集成了课程表管理、成绩查询、校园资讯等功能。

### 技术栈
- **开发语言**: Kotlin
- **最低SDK版本**: 26 (Android 8.0)
- **目标SDK版本**: 33 (Android 13)
- **构建工具**: Gradle 8.7

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 8 或更高版本
- Android SDK 26+

## 📦 项目结构

```
HITA_Agent/
├── app/                    # 主应用模块
│   ├── src/main/
│   │   ├── java/          # Kotlin源代码
│   │   ├── res/           # 资源文件
│   │   └── AndroidManifest.xml
├── component/             # 通用组件模块
├── style/                 # UI样式模块
├── user/                  # 用户模块
├── sync/                  # 同步模块
├── theta/                 # 业务功能模块
└── gradle/                # Gradle配置
```

## 🔧 开发设置

### 1. 克隆项目
```bash
git clone <repository-url>
cd HITA_Agent
```

### 2. Android Studio配置
1. 打开Android Studio
2. 选择 "Open an Existing Project"
3. 选择项目根目录
4. 等待Gradle同步完成

### 3. 构建项目
```bash
./gradlew assembleDebug
```

### 4. 运行应用
1. 连接Android设备或启动模拟器
2. 点击 Run 按钮 (▶️)
3. 选择目标设备

## 🎯 核心功能

### 1. 用户认证
- 用户注册和登录
- 会话管理
- 个人资料管理

### 2. 课程管理
- 课程表查看
- 课程添加和编辑
- 课程提醒设置

### 3. 成绩查询
- 成绩查询功能
- 成绩统计分析
- GPA计算

### 4. 校园资讯
- 新闻资讯浏览
- 活动通知
- 公告查看

### 5. AI助手
- 智能对话功能
- 文件处理
- 日程管理

## 🔑 关键配置

### BuildConfig字段
```gradle
HOA_BASE_URL          // 后端API地址
AGENT_BACKEND_BASE_URL // AI服务地址
HOA_API_KEY           // API密钥
UPDATE_URL            // 更新检查URL
```

### 权限说明
- `INTERNET`: 网络访问
- `ACCESS_NETWORK_STATE`: 网络状态检查
- `WRITE_EXTERNAL_STORAGE`: 文件写入
- `READ_EXTERNAL_STORAGE`: 文件读取
- `POST_NOTIFICATIONS`: 通知权限

## 🧪 测试

### 单元测试
```bash
./gradlew test
```

### 集成测试
```bash
./gradlew connectedAndroidTest
```

### 测试覆盖率
```bash
./gradlew jacocoTestReport
```

## 📱 模块说明

### app模块
主应用模块，包含：
- UI界面
- 业务逻辑
- 数据管理

### component模块
通用组件，提供：
- 基础类定义
- 工具方法
- 通用UI组件

### style模块
UI样式模块，包含：
- 主题配置
- 自定义View
- 对话框组件

### user模块
用户模块，处理：
- 用户认证
- 个人信息管理
- 用户偏好设置

### sync模块
同步模块，负责：
- 数据同步
- 冲突解决
- 离线支持

### theta模块
业务模块，实现：
- 课程管理
- 成绩查询
- 校园资讯

## 🐛 调试技巧

### 1. 日志查看
```kotlin
Log.d("MyTag", "Debug message")
Log.i("MyTag", "Info message")
Log.w("MyTag", "Warning message")
Log.e("MyTag", "Error message", exception)
```

### 2. 性能分析
- 使用 Android Profiler
- 检查内存使用情况
- 分析CPU占用

### 3. 网络调试
- 使用 Charles Proxy
- 抓包分析网络请求
- 检查API响应

## 📊 性能优化

### 1. 内存优化
- 及时释放资源
- 使用对象池
- 避免内存泄漏

### 2. 启动优化
- 延迟初始化非必要组件
- 异步加载资源
- 优化Application启动

### 3. 网络优化
- 使用缓存
- 批量请求
- 压缩数据

## 🔒 安全最佳实践

### 1. 数据加密
```kotlin
// 使用加密SharedPreferences
val securePrefs = EncryptedSharedPreferences.create(
    "user_prefs",
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 2. 网络安全
- 使用HTTPS协议
- 验证SSL证书
- 防止中间人攻击

### 3. 代码混淆
```gradle
buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

## 📖 常见问题

### Q: 编译时出现"Out of memory"错误？
A: 增加Gradle内存配置：
```gradle
org.gradle.jvmargs=-Xmx4096m
```

### Q: 无法连接到后端服务？
A: 检查网络配置和防火墙设置，确保能访问API地址。

### Q: 应用崩溃？
A: 查看logcat日志，定位崩溃原因，检查空指针异常。

### Q: 如何添加新功能？
A: 参考《CODING_STANDARDS.md》文档，遵循项目架构和编码规范。

## 🤝 贡献指南

### 代码贡献流程
1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

### 代码规范
- 遵循Kotlin编码规范
- 添加必要的注释
- 编写单元测试
- 更新相关文档

## 📝 更新日志

### 最新版本: 2.0.2
- ✅ 完成包名重构 (stupidtree -> limpu)
- ✅ 修复编译warnings
- ✅ 添加性能优化工具类
- ✅ 改进空安全处理
- ✅ 完善文档和注释

## 📞 联系方式

- 项目主页: [GitHub Repository]
- 问题反馈: [Issues]
- 讨论区: [Discussions]

---

**Happy Coding! 🎉**
