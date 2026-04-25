package com.limpu.hitax.agent.subject

import androidx.lifecycle.LifecycleOwner
import com.limpu.hitax.data.model.resource.CourseResourceItem

data class SubjectReadmeAgentInput(
    val owner: LifecycleOwner,
    val subjectId: String,
    val courseCode: String?,
    val courseName: String?,
    val campus: String?,
)

data class SubjectReadmeAgentOutput(
    val candidates: List<CourseResourceItem>,
)
