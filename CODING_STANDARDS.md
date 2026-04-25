# HITA Agent 代码规范和最佳实践

## 📋 项目概述

HITA Agent 是一个基于 Android 的智能助手应用，采用 Kotlin 编写，模块化架构设计。

## 🏗️ 项目架构

### 模块结构
- **app**: 主应用模块，包含UI和业务逻辑
- **component**: 通用组件模块，提供基础功能
- **style**: 样式模块，包含UI组件和主题
- **user**: 用户模块，处理用户相关功能
- **sync**: 同步模块，处理数据同步
- **theta**: 功能模块，包含特定业务功能

### 技术栈
- **语言**: Kotlin
- **架构**: MVVM + Repository Pattern
- **异步**: Kotlin Coroutines + Flow
- **依赖注入**: 手动依赖注入
- **网络**: Retrofit + OkHttp
- **数据库**: Room
- **UI**: ViewBinding + Material Design

## 🎯 编码规范

### 1. 命名规范

#### 类命名
- 使用 PascalCase
- Activity/Fragment: `FeatureActivity`, `FeatureFragment`
- ViewModel: `FeatureViewModel`
- Adapter: `FeatureAdapter`
- 工具类: `FeatureUtils` 或 `FeatureHelper`

#### 变量命名
- 使用 camelCase
- 常量: UPPER_SNAKE_CASE
- 私有变量: 可选择性添加前缀 `_`
- LiveData: `featureData` 或 `featureLiveData`

#### 包命名
- 全小写，不包含下划线
- 按功能分组: `com.limpu.hitax.feature`

### 2. 代码组织

#### 文件结构
```kotlin
// 1. 包声明
package com.example.app

// 2. 导入语句
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// 3. 类注释
/**
 * 类的描述
 */
class MyClass {

    // 4. 伴生对象
    companion object {
        private const val TAG = "MyClass"
    }

    // 5. 属性
    private val property: String = "value"

    // 6. 构造函数
    constructor()

    // 7. 公共方法
    fun publicMethod() {}

    // 8. 私有方法
    private fun privateMethod() {}
}
```

### 3. 最佳实践

#### 3.1 空安全处理
```kotlin
// ✅ 推荐：使用安全调用
val name = user?.name ?: "Unknown"

// ❌ 避免：使用非空断言
val name = user!!.name

// ✅ 推荐：使用 let 处理空值
user?.let {
    // 处理非空情况
}

// ✅ 推荐：自定义扩展函数
context?.showSafeToast("Message")
```

#### 3.2 协程使用
```kotlin
// ✅ 推荐：使用 viewModelScope
viewModelScope.launch {
    // 自动取消，避免内存泄漏
}

// ❌ 避免：使用 GlobalScope
GlobalScope.launch { // 不会自动取消
    // 处理逻辑
}

// ✅ 推荐：使用 withContext 切换线程
withContext(Dispatchers.IO) {
    // IO 操作
}
```

#### 3.3 内存管理
```kotlin
// ✅ 推荐：使用弱引用
private var listener: WeakReference<Listener>? = null

// ✅ 推荐：及时清理资源
override fun onDestroy() {
    super.onDestroy()
    // 清理资源
    binding = null
    listener = null
}

// ✅ 推荐：使用资源清理工具
ResourceCleaner.cleanOldCacheFiles(context)
```

#### 3.4 错误处理
```kotlin
// ✅ 推荐：使用 try-catch-finally
try {
    // 可能抛出异常的代码
} catch (e: Exception) {
    Log.e(TAG, "操作失败", e)
    // 处理异常
} finally {
    // 清理资源
}

// ✅ 推荐：使用 Result 封装结果
fun performOperation(): Result<Data> {
    return try {
        Result.success(doOperation())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 4. 性能优化

#### 4.1 列表操作
```kotlin
// ✅ 推荐：使用 Sequence 处理大数据集
val result = largeList
    .asSequence()
    .filter { it.isValid }
    .map { it.transformed }
    .toList()

// ✅ 推荐：批量操作
val results = PerformanceUtils.processBatched(items) { batch ->
    // 处理批次
}
```

#### 4.2 内存优化
```kotlin
// ✅ 推荐：使用对象池
object BitmapPool {
    private val pool = HashMap<Int, Bitmap>()

    fun get(width: Int, height: Int): Bitmap? {
        return pool[width * height]
    }
}

// ✅ 推荐：延迟初始化
private val heavyObject by lazy { HeavyObject() }
```

### 5. 测试规范

#### 5.1 单元测试
```kotlin
@Test
fun `should return correct result when input is valid`() {
    // Given
    val input = "test"
    val expected = "TEST"

    // When
    val result = processor.process(input)

    // Then
    assertEquals(expected, result)
}
```

### 6. 安全规范

#### 6.1 网络安全
```kotlin
// ✅ 推荐：使用 HTTPS
val url = "https://api.example.com"

// ❌ 避免：硬编码敏感信息
val apiKey = "hardcoded_key" // 不要这样做

// ✅ 推荐：使用 BuildConfig
val apiKey = BuildConfig.API_KEY
```

#### 6.2 数据安全
```kotlin
// ✅ 推荐：加密敏感数据
val encryptedData = EncryptionUtils.encrypt(data)

// ✅ 推荐：使用 SharedPreferences 的加密版本
val securePrefs = EncryptedSharedPreferences.create(...)
```

## 📝 代码审查清单

### 提交前检查
- [ ] 代码编译无错误
- [ ] 无明显的性能问题
- [ ] 正确处理了空值情况
- [ ] 没有内存泄漏风险
- [ ] 异常处理适当
- [ ] 日志记录合理
- [ ] 资源正确释放
- [ ] 命名规范统一
- [ ] 注释清晰准确

### 性能检查
- [ ] 避免不必要的对象创建
- [ ] 使用适当的数据结构
- [ ] 避免在循环中进行重复计算
- [ ] 合理使用缓存
- [ ] 异步操作不阻塞主线程

### 安全检查
- [ ] 验证用户输入
- [ ] 不暴露敏感信息
- [ ] 使用安全的网络协议
- [ ] 正确处理权限请求

## 🔧 工具和实用类

### 性能工具
- `PerformanceUtils`: 性能监控和优化工具
- `ResourceCleaner`: 资源清理工具
- `NullSafetyExtensions`: 空安全扩展函数

### 使用示例
```kotlin
// 性能监控
val result = PerformanceUtils.measurePerformance("database_query") {
    database.query()
}

// 资源清理
ResourceCleaner.cleanOldCacheFiles(context, 7 * 24 * 60 * 60 * 1000L)

// 安全操作
context?.showSafeToast("操作成功")
```

## 📚 参考资源

- [Kotlin 官方文档](https://kotlinlang.org/docs/)
- [Android 开发最佳实践](https://developer.android.com/quality)
- [Kotlin 协程指南](https://developer.android.com/kotlin/coroutines)
- [Material Design 规范](https://material.io/design)

## 🔄 版本控制

### 分支策略
- `main`: 主分支，保持稳定
- `develop`: 开发分支
- `feature/*`: 功能分支
- `bugfix/*`: 修复分支

### 提交信息格式
```
feat: 添加新功能
fix: 修复bug
refactor: 重构代码
style: 代码格式调整
docs: 文档更新
perf: 性能优化
test: 测试相关
chore: 构建/工具链相关
```

---

**注意**: 本文档会持续更新，请定期查看最新版本。
