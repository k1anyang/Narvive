package com.narvive.app.ui.screen.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.data.datastore.NarviveDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreSettingsViewModel @Inject constructor(
    private val dataStore: NarviveDataStore,
) : ViewModel() {

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    data class UiState(
        val volumeKeyPageTurn: Boolean = true,
        val bookOpenAnimation: Boolean = false,
        val screenOffMinutes: Int = 5,
        val restReminderEnabled: Boolean = false,
        val restReminderMinutes: Int = 30,
        val progressDisplayMode: String = "percentage",
        val publisherStyles: Boolean = true,
        val showTopInfo: Boolean = true,
        val showBottomInfo: Boolean = true,
        val batteryPercent: Boolean = false,
    )

    val uiState: StateFlow<UiState> = combine(
        combine(
            dataStore.volumeKeyPageTurn,
            dataStore.bookOpenAnimation,
            dataStore.screenOffMinutes,
        ) { volumeKey, bookAnim, screenOff -> Triple(volumeKey, bookAnim, screenOff) },
        combine(
            dataStore.restReminderEnabled,
            dataStore.restReminderMinutes,
            dataStore.progressDisplayMode,
        ) { restOn, restMin, progress -> Triple(restOn, restMin, progress) },
        combine(
            dataStore.showTopInfo,
            dataStore.showBottomInfo,
            dataStore.batteryPercent,
        ) { top, bottom, battery -> Triple(top, bottom, battery) },
        dataStore.publisherStyles,
    ) { g1, g2, g3, publisherStyles ->
        UiState(
            volumeKeyPageTurn = g1.first,
            bookOpenAnimation = g1.second,
            screenOffMinutes = g1.third,
            restReminderEnabled = g2.first,
            restReminderMinutes = g2.second,
            progressDisplayMode = g2.third,
            publisherStyles = publisherStyles,
            showTopInfo = g3.first,
            showBottomInfo = g3.second,
            batteryPercent = g3.third,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        // 等全部偏好读完后才渲染开关，避免打开页面时 Switch 从默认值动画到真实值
        viewModelScope.launch {
            dataStore.volumeKeyPageTurn.first()
            dataStore.bookOpenAnimation.first()
            dataStore.screenOffMinutes.first()
            dataStore.restReminderEnabled.first()
            dataStore.restReminderMinutes.first()
            dataStore.progressDisplayMode.first()
            dataStore.publisherStyles.first()
            dataStore.showTopInfo.first()
            dataStore.showBottomInfo.first()
            dataStore.batteryPercent.first()
            _loaded.value = true
        }
    }

    fun setVolumeKeyPageTurn(value: Boolean) {
        viewModelScope.launch { dataStore.setVolumeKeyPageTurn(value) }
    }

    fun setBookOpenAnimation(value: Boolean) {
        viewModelScope.launch { dataStore.setBookOpenAnimation(value) }
    }

    fun setScreenOffMinutes(value: Int) {
        viewModelScope.launch { dataStore.setScreenOffMinutes(value) }
    }

    fun setRestReminder(enabled: Boolean, minutes: Int = 30) {
        viewModelScope.launch {
            dataStore.setRestReminderEnabled(enabled)
            dataStore.setRestReminderMinutes(minutes)
        }
    }

    fun setProgressDisplayMode(value: String) {
        viewModelScope.launch { dataStore.setProgressDisplayMode(value) }
    }

    fun setPublisherStyles(value: Boolean) {
        viewModelScope.launch { dataStore.setPublisherStyles(value) }
    }

    fun setShowTopInfo(value: Boolean) {
        viewModelScope.launch { dataStore.setShowTopInfo(value) }
    }

    fun setShowBottomInfo(value: Boolean) {
        viewModelScope.launch { dataStore.setShowBottomInfo(value) }
    }

    fun setBatteryPercent(value: Boolean) {
        viewModelScope.launch { dataStore.setBatteryPercent(value) }
    }
}
