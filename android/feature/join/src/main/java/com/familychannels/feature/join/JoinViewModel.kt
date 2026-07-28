package com.familychannels.feature.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.repo.FamilyRepository
import com.familychannels.domain.usecase.JoinFamilyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class JoinUiState(
    val code: String = "",
    val children: List<ChildProfile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val sessionReady: Boolean = false,
)

class JoinViewModel(
    private val joinFamily: JoinFamilyUseCase,
    private val repo: FamilyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(code = value, error = null)
    }

    fun submitCode() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { joinFamily(_state.value.code.trim().uppercase()) }
                .onSuccess { kids ->
                    _state.value = _state.value.copy(loading = false, children = kids)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.message ?: "join_failed",
                    )
                }
        }
    }

    fun selectChild(childId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching {
                repo.createSession(_state.value.code.trim().uppercase(), childId)
            }.onSuccess {
                _state.value = _state.value.copy(loading = false, sessionReady = true)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "session_failed",
                )
            }
        }
    }
}
