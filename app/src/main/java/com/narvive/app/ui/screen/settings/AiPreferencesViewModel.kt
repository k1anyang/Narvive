package com.narvive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.service.ai.AiPrefState
import com.narvive.app.service.ai.AiProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InductStatus { Idle, Loading, Success }

@HiltViewModel
class AiPreferencesViewModel @Inject constructor(
    private val profileStore: AiProfileStore,
) : ViewModel() {
    val state: StateFlow<AiPrefState> = profileStore.state

    private val _inductStatus = MutableStateFlow(InductStatus.Idle)
    val inductStatus: StateFlow<InductStatus> = _inductStatus.asStateFlow()

    fun setEnabled(enabled: Boolean) { viewModelScope.launch { profileStore.setEnabled(enabled) } }
    fun setMode(mode: String) { viewModelScope.launch { profileStore.setMode(mode) } }
    fun setManualProfile(text: String) { viewModelScope.launch { profileStore.setManualProfile(text) } }
    fun clearProfile() { viewModelScope.launch { profileStore.clearProfile() } }

    fun refreshAuto() {
        if (_inductStatus.value == InductStatus.Loading) return
        viewModelScope.launch {
            _inductStatus.value = InductStatus.Loading
            profileStore.refreshAuto()
            _inductStatus.value = InductStatus.Success
            delay(3000)
            _inductStatus.value = InductStatus.Idle
        }
    }
}
