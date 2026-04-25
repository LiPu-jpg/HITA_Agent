package com.limpu.hitax.utils

object ColorTools {
    private const val TIME_INTERVAL = 1000 * 60.toLong()
    var lastTimestamp: Long = 0
    var colors_material = arrayOf(
        "#ec407a",
        "#FF9E00",
        "#7c4dff",
        "#536dfe",
        "#2196f3",
        "#26c6da",
        "#009688",
        "#7cba59",
        "#E96D71",
    )
    var colorsQueue = mutableListOf<String>()

    fun randomColorMaterial(): Int {
        //超过时间，重置队列
        if (colorsQueue.isEmpty() || lastTimestamp + TIME_INTERVAL < System.currentTimeMillis()) {
            colorsQueue.clear()
            colorsQueue.addAll(colors_material)
            colorsQueue.shuffle()
        }
        lastTimestamp = System.currentTimeMillis()
        return parseHexColor(colorsQueue.removeAt(0))
    }


    fun changeAlpha(color: Int, alpha: Float): Int {
        val alphaChannel = (255 * alpha).toInt().coerceIn(0, 255)
        return (alphaChannel shl 24) or (color and 0x00FFFFFF)
    }

    private fun parseHexColor(raw: String): Int {
        val normalized = raw.removePrefix("#")
        val parsed = normalized.toLongOrNull(16)
            ?: throw IllegalArgumentException("Invalid color: $raw")
        return when (normalized.length) {
            6 -> (0xFF000000 or parsed).toInt()
            8 -> parsed.toInt()
            else -> throw IllegalArgumentException("Invalid color length: $raw")
        }
    }

}
