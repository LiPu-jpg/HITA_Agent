# 外部课程资料检索功能设计

**日期**: 2026-05-16  
**分支**: feat/external-resource-search  
**状态**: 已自审，待用户审阅

## 概述

为 HITA_Agent 新增独立的外部课程资料搜索页面，支持同时检索两个来源：

- **HITCS** (https://github.com/HITLittleZheng/HITCS) — GitHub 课程资料仓库，684 stars，3.5GB，按院系/课程分层
- **薪火笔记社** (https://fireworks.jwyihao.top/) — VitePress 笔记站 + AList 文件服务器，50+ 课程

搜索结果统一合并展示，支持课程级搜索 + 目录浏览 + 直接下载。

## 需求

| 项目 | 决定 |
|------|------|
| 功能入口 | 独立搜索页面（不接入 AI Agent） |
| 搜索模式 | 统一搜索，结果合并展示 |
| 搜索深度 | 课程级搜索 + 目录树浏览 |
| 下载方式 | 点击直接调用系统浏览器/下载器 |

## 架构方案

扩展现有 HOA 搜索模式（方案 A）：新增独立 WebSource + Repository，复用 Jsoup + LiveData + DataState 模式。

### 数据流

```
UI (ExternalResourceSearchActivity)
  --> viewModel.search(query)
  --> ViewModel.queryLiveData.switchMap
  --> ExternalResourceRepository.searchCourses()
      ├── HITCSWebSource.searchCourses()   [GitHub Tree API, 模糊匹配]
      └── FireworksWebSource.searchCourses() [AList API, 两级遍历]
      └── MediatorLiveData 合并结果
  --> Activity 观察 resultsLiveData
  --> adapter 更新列表
```

## 数据模型

新增文件：`data/model/resource/ExternalResourceItem.kt`

```kotlin
/** 资源来源标识 */
enum class ResourceSource { HITCS, FIREWORKS }

/** 课程级搜索结果 */
data class ExternalCourseItem(
    var courseName: String = "",       // 课程名
    var category: String = "",         // 所属分类（院系/公共课）
    var source: ResourceSource = ResourceSource.HITCS,
    var path: String = "",             // API 路径，用于后续目录浏览
    var description: String = "",      // 简要描述
)

/** 目录/文件浏览项 */
data class ExternalResourceEntry(
    var name: String = "",             // 文件/目录名
    var isDir: Boolean = false,        // 是否为目录
    var path: String = "",             // 完整路径
    var size: Long = 0,                // 文件大小（字节）
    var downloadUrl: String = "",      // 下载链接
    var source: ResourceSource = ResourceSource.HITCS,
)
```

## WebSource 层

### HITCSWebSource

新增文件：`data/source/web/HITCSWebSource.kt`（Kotlin `object`）

**搜索课程**：
- 调用 GitHub Tree API：`GET https://api.github.com/repos/HITLittleZheng/HITCS/git/trees/main?recursive=1`
- 解析返回的目录树，提取所有二级目录（分类/课程名）
- 按关键词对课程名做模糊匹配（包含即匹配）
- 返回 `List<ExternalCourseItem>`

**浏览目录**：
- 调用 GitHub Contents API：`GET https://api.github.com/repos/HITLittleZheng/HITCS/contents/{path}`
- 解析 JSON 数组，每个元素映射为 `ExternalResourceEntry`
- 文件的 `download_url` 直接可用于下载

**限流策略**：
- GitHub 未认证 API 限制 60 次/小时
- Tree API 结果缓存在内存中（一次搜索加载完整目录树，后续搜索复用）
- 目录浏览（Contents API）不做缓存，因为调用频率低

### FireworksWebSource

新增文件：`data/source/web/FireworksWebSource.kt`（Kotlin `object`）

**搜索课程**：
- AList API：`POST https://olist-eo.jwyihao.top/api/fs/list`
- 第一次搜索时加载 `/Fireworks` 根目录获取所有院系
- 遍历每个院系目录获取课程列表（可并行）
- 缓存完整的 院系→课程 映射到内存
- 按关键词对课程名做模糊匹配
- 返回 `List<ExternalCourseItem>`

**浏览目录**：
- 同一 AList API，`path` 参数指向具体课程子目录
- 响应中 `data.content` 数组映射为 `List<ExternalResourceEntry>`

**下载链接**：
- 使用 `https://alist-d.jwyihao.top/d/Fireworks/{encoded_path}` 拼接
- EdgeOne CDN，速度快

## Repository 层

新增文件：`data/repository/ExternalResourceRepository.kt`

```kotlin
class ExternalResourceRepository @Inject constructor() {

    fun searchCourses(query: String): LiveData<DataState<List<ExternalCourseItem>>>

    fun listDirectory(
        path: String,
        source: ResourceSource
    ): LiveData<DataState<List<ExternalResourceEntry>>>
}
```

**searchCourses 合并逻辑**：
1. 分别调用 `HITCSWebSource.searchCourses(query)` 和 `FireworksWebSource.searchCourses(query)`
2. 使用 `MediatorLiveData` 监听两个来源
3. 任一来源成功即更新结果（容错降级：一个来源失败不影响另一个）
4. 两个来源都返回后合并列表，按课程名排序

**listDirectory**：根据 `source` 参数路由到对应 WebSource。

## UI 层

### Activity

新增文件：`ui/resource/ExternalResourceSearchActivity.kt`

- 继承 `HiltBaseActivity<ActivityExternalResourceSearchBinding>`
- `@AndroidEntryPoint` 注解
- `by viewModels()` 获取 ViewModel
- 搜索栏（EditText + 搜索按钮）+ SwipeRefreshLayout + RecyclerView + 空状态 TextView
- 搜索触发：IME_ACTION_SEARCH 或按钮点击
- 结果项点击：在同一 Activity 内切换为目录浏览模式（搜索栏隐藏，显示面包屑导航 + 返回按钮）
- 目录项点击：
  - 目录 → 递归浏览子目录
  - 文件 → `Intent.ACTION_VIEW` 打开下载链接

### ViewModel

新增文件：`ui/resource/ExternalResourceSearchViewModel.kt`

```kotlin
@HiltViewModel
class ExternalResourceSearchViewModel @Inject constructor(
    private val repository: ExternalResourceRepository
) : ViewModel() {

    private val queryLiveData = MutableLiveData<String>()
    val resultsLiveData: LiveData<DataState<List<ExternalCourseItem>>> =
        queryLiveData.switchMap { repository.searchCourses(it) }

    fun search(query: String) {
        if (query.isBlank()) return
        queryLiveData.value = query.trim()
    }
}
```

### Adapter

`ExternalCourseAdapter` 继承 `BaseListAdapter<ExternalCourseItem, Holder>`：
- `getViewBinding` — inflate `item_external_course.xml`
- `bindHolder` — 显示课程名、分类、来源标签（HITCS / 薪火笔记社）

`ExternalResourceEntryAdapter` 继承 `BaseListAdapter<ExternalResourceEntry, Holder>`：
- `getViewBinding` — inflate `item_external_resource_entry.xml`
- `bindHolder` — 显示文件/目录图标 + 名称 + 大小

### 布局文件

| 文件 | 用途 |
|------|------|
| `activity_external_resource_search.xml` | 搜索页主布局（Toolbar + 搜索栏 + SwipeRefresh + RecyclerView + 空状态） |
| `item_external_course.xml` | 搜索结果项（课程名 + 分类 + 来源标签） |
| `item_external_resource_entry.xml` | 目录浏览项（图标 + 名称 + 大小） |

### 入口

在现有 HOA 课程资源搜索页面（`CourseResourceSearchActivity`）的 Toolbar 中增加一个"外部资料"按钮，点击跳转到 `ExternalResourceSearchActivity`。这样用户在同一搜索流程中可以无缝切换 HOA 和外部来源。

## 文件清单

| 新增文件 | 层级 |
|----------|------|
| `data/model/resource/ExternalResourceItem.kt` | 数据模型 |
| `data/source/web/HITCSWebSource.kt` | 网络层 |
| `data/source/web/FireworksWebSource.kt` | 网络层 |
| `data/repository/ExternalResourceRepository.kt` | 仓库层 |
| `ui/resource/ExternalResourceSearchActivity.kt` | UI |
| `ui/resource/ExternalResourceSearchViewModel.kt` | UI |
| `res/layout/activity_external_resource_search.xml` | 布局 |
| `res/layout/item_external_course.xml` | 布局 |
| `res/layout/item_external_resource_entry.xml` | 布局 |

## 错误处理

- 网络超时：显示空状态 + 错误提示，支持下拉刷新重试
- 单来源失败：另一个来源正常展示，失败来源在结果中标记或静默忽略
- GitHub API 限频：Tree API 缓存 1 小时，超限后提示"请稍后再试"
- 空结果：显示"未找到相关课程资料"

## 非目标（YAGNI）

- 不实现 AI Agent 工具集成
- 不实现文件预览（仅直接下载）
- 不实现离线缓存/本地存储
- 不实现用户登录/收藏功能
