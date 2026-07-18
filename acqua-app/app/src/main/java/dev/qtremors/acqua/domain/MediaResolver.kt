package dev.qtremors.acqua.domain

fun interface MediaResolver {
    fun resolve(url: String): List<ResolvedMedia>
}
