package com.stupidtree.hitax.data.repository

import androidx.lifecycle.LiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseReadmeData
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.data.model.resource.CourseStructureSummary
import com.stupidtree.hitax.data.model.resource.ValidateReadmeResult
import com.stupidtree.hitax.data.source.web.HoaResourceSource
import org.json.JSONArray

class HoaRepository private constructor() {
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

    companion object {
        private var instance: HoaRepository? = null

        fun getInstance(): HoaRepository {
            if (instance == null) {
                instance = HoaRepository()
            }
            return instance!!
        }
    }
}