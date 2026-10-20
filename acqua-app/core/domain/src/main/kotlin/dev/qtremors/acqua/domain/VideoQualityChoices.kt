package dev.qtremors.acqua.domain

fun videoQualityChoices(selectedHeight: Int, inspectedHeights: List<Int> = emptyList()): List<Int> {
    val heights = inspectedHeights.filter { it > 0 }.ifEmpty { listOf(2160, 1440, 1080, 720, 480, 360) }
    return listOf(0) + (heights + selectedHeight).filter { it > 0 }.distinct().sortedDescending()
}
