package com.stupidtree.hitax.agent.subject

import com.stupidtree.hitax.agent.core.AgentTool
import com.stupidtree.hitax.agent.core.AgentToolResult
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.utils.CourseResourceLinker

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
