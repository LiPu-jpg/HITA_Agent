package com.limpu.hitax.agent.subject

import com.limpu.hitax.agent.core.AgentTool
import com.limpu.hitax.agent.core.AgentToolResult
import com.limpu.hitax.data.model.resource.CourseResourceItem
import com.limpu.hitax.utils.CourseResourceLinker

class ResolveCourseCandidatesTool : AgentTool<SubjectReadmeAgentInput, List<CourseResourceItem>> {
    override val name: String = "resolve_course_candidates"

    override fun execute(
        input: SubjectReadmeAgentInput,
        onResult: (AgentToolResult<List<CourseResourceItem>>) -> Unit,
    ) {
        CourseResourceLinker.resolveCandidates(
            owner = input.owner,
            courseCodeRaw = input.courseCode,
            courseNameRaw = input.courseName,
            campus = input.campus,
        ) { candidates ->
            onResult(AgentToolResult.success(candidates))
        }
    }
}
