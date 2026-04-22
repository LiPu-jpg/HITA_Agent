# HITA 课表（维护分支）

[App 下载（Releases）](https://github.com/LiPu-jpg/HITA_L/releases/latest)

## 项目背景
项目最初来自哈尔滨工业大学（深圳）2018 级本科生大一年度立项，原名 HITSZ 助手，重构版改名为 HITA 课表。
该项目长期无人维护，本分支为接手维护的延续版本，同时附带了本研抓包逆向出来的api清单（由于更新时为非选课时间，部分选课api不能完全确认功能，同时部分api学校也没做完），便于后续更新。

## 应用简介
这是面向哈尔滨工业大学（深圳）学生的工具类 APP（非官方）。

### 当前主要功能
- **课表与日程**：导入课表、按周查看、课程详情
- **教务服务**：成绩查询、学分绩与排名、空教室查询
- **选课助手**：定时发包抢课（按设定时间发 5 次）
- **课程资源**：应用内搜索课程资料与 README、支持追加型投稿
- **教师搜索**：优先使用课程资源数据，同时提供教师主页检索入口
- **考试**：暂无官方接口，提供考试备忘录
- **AI 助手**：基于 ReAct 框架的智能问答，支持课程查询、教师搜索、课表查询、评价提交等功能

## AI 助手功能说明

### 支持的智能工具
AI 助手基于 ReAct 框架，支持以下工具调用：

1. **课表查询** (`get_timetable`) - 查询今日/明日/任意日期的课程安排
2. **课程搜索** (`search_course`) - 搜索课程代码和名称
3. **课程详情** (`get_course_detail`) - 获取课程 README、评价、教师信息等详细内容
4. **教师搜索** (`search_teacher`) - 搜索教师信息和主页
5. **网页搜索** (`web_search`) - Brave 搜索引擎
6. **AI 搜索** (`brave_answer`) - Brave AI 智能回答
7. **知识库查询** (`rag_search`) - 查询学校相关知识库
8. **网页爬取** (`crawl_page`/`crawl_site`) - 爬取网页内容
9. **提交评价** (`submit_review`) - 提交课程评价/学习笔记/PR（Pull Request）
10. **添加日程** (`add_activity`) - 添加日历提醒

### 技术架构
- **前端**：Android Kotlin + Retrofit
- **LLM**：MiniMax API（支持 deepseek-r1 等模型）
- **后端服务**：
  - pr-server：课程资源服务（GitHub HOA 仓库交互）
  - agent-backend：AI 工具编排服务（搜索、爬取、RAG 等）
- **数据流**：课程查询直接访问 pr-server，其他工具通过 agent-backend 编排

## 数据与版权说明
- 课程与课表数据来自教务系统，本应用不额外采集或上传。
- 课程资料来源 HOA（校内民间开源组织），欢迎同学参与贡献。官网：hoa.moe
- 如有问题请联系：2720649216@qq.com

## 用到第三方开源库
- 加载效果按钮：[LoadingButtonAndroid](https://github.com/leandroBorgesFerreira/LoadingButtonAndroid)
- 显示多行的 CollapsingToolbarLayout：[multiline-collapsingtoolbar](https://github.com/opacapp/multiline-collapsingtoolbar)
- θ 社区上传图片压缩：[Luban](https://github.com/Curzibn/Luban)
- 今日页下拉交互：[PullLoadXiaochengxu](https://github.com/LucianZhang/PullLoadXiaochengxu)

## License

[MIT](LICENSE) © Stupid Tree
