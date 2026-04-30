package com.limpu.hitax.agent.tools

class ReActToolRegistry {
    private val tools = mutableMapOf<String, ReActTool>()

    fun register(name: String, tool: ReActTool) {
        tools[name.lowercase()] = tool
    }

    fun get(name: String): ReActTool? = tools[name.lowercase()]

    companion object {
        fun createDefault(): ReActToolRegistry = ReActToolRegistry().apply {
            register("get_timetable", GetTimetableTool())
            register("add_activity", AddActivityTool())
            register("search_course", SearchCourseTool())
            register("get_course_detail", GetCourseDetailTool())
            register("search_teacher", SearchTeacherTool())
            register("web_search", WebSearchTool())
            register("brave_answer", BraveAnswerTool())
            register("rag_search", RagSearchTool())
            register("crawl_page", CrawlPageTool())
            register("crawl_site", CrawlSiteTool())
            register("crawl_status", CrawlStatusTool())
            register("submit_review", SubmitReviewTool())
        }
    }
}
