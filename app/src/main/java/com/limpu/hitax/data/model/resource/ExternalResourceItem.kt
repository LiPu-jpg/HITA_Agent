package com.limpu.hitax.data.model.resource

enum class ResourceSource { HITCS, FIREWORKS }

data class ExternalCourseItem(
    var courseName: String = "",
    var category: String = "",
    var source: ResourceSource = ResourceSource.HITCS,
    var path: String = "",
    var description: String = "",
)

data class ExternalResourceEntry(
    var name: String = "",
    var isDir: Boolean = false,
    var path: String = "",
    var size: Long = 0,
    var downloadUrl: String = "",
    var source: ResourceSource = ResourceSource.HITCS,
)
