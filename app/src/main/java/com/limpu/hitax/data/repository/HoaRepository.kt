package com.limpu.hitax.data.repository

import androidx.lifecycle.LiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.CourseReadmeData
import com.limpu.hitax.data.model.resource.CourseResourceItem
import com.limpu.hitax.data.model.resource.CourseStructureSummary
import com.limpu.hitax.data.model.resource.ValidateReadmeResult
import com.limpu.hitax.data.source.web.HoaResourceSource
import org.json.JSONArray

class HoaRepository constructor() {
    fun searchCourses(query: String, campus: String? = null): LiveData<DataState<List<CourseResourceItem>>> {
        return HoaResourceSource.searchCourses(query, campus)
    }

    fun getCourseReadme(repoName: String, campus: String? = null): LiveData<DataState<CourseReadmeData>> {
        return HoaResourceSource.getCourseReadme(repoName, campus)
    }

    fun getCourseStructure(repoName: String, campus: String? = null): LiveData<DataState<CourseStructureSummary>> {
        return HoaResourceSource.getCourseStructure(repoName, campus)
    }

    fun validateReadme(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        readmeMd: String
    ): LiveData<DataState<ValidateReadmeResult>> {
        return HoaResourceSource.validateReadme(repoName, courseCode, courseName, repoType, readmeMd)
    }
    fun submitOps(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        ops: JSONArray,
        campus: String? = null,
    ): LiveData<DataState<String>> {
        return HoaResourceSource.submitOps(repoName, courseCode, courseName, repoType, ops, campus)
    }

}