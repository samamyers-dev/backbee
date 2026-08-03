package dev.backbee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Bridges hand-rolled wiring to `viewModel()`.
 *
 * The app has no DI framework, but ViewModels still want to survive
 * configuration changes, so this hands the container to a constructor the
 * platform would otherwise insist on calling reflectively.
 */
@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> viewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
