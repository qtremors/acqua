package dev.qtremors.acqua.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class ViewModelFactory<T : ViewModel>(
    private val create: (CreationExtras) -> T
) : ViewModelProvider.Factory {
    override fun <R : ViewModel> create(modelClass: Class<R>, extras: CreationExtras): R {
        val instance = create(extras)
        require(modelClass.isAssignableFrom(instance.javaClass))
        @Suppress("UNCHECKED_CAST")
        return instance as R
    }
}
