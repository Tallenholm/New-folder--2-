package com.mapledger.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mapledger.app.AppContainer
import com.mapledger.app.MapLedgerApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as MapLedgerApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}
