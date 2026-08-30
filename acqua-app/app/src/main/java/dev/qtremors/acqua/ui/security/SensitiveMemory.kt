package dev.qtremors.acqua.ui.security

object SensitiveMemory {
    @Volatile
    var clearDelegate: (() -> Unit)? = null

    fun clear() = clearDelegate?.invoke() ?: Unit
}
