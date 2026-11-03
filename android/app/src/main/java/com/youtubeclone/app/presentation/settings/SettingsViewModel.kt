package com.youtubeclone.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.usecase.ObserveShareUsageStatsUseCase
import com.youtubeclone.app.domain.usecase.SetShareUsageStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeShareUsageStats: ObserveShareUsageStatsUseCase,
    private val setShareUsageStats: SetShareUsageStatsUseCase,
) : ViewModel() {

    /** null tant que la préférence n'est pas lue : l'interrupteur n'est pas encore affiché. */
    val shareUsageStats: StateFlow<Boolean?> = observeShareUsageStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onShareUsageStatsChange(enabled: Boolean) {
        viewModelScope.launch { setShareUsageStats(enabled) }
    }
}
