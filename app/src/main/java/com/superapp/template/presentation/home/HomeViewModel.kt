package com.startup.template.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.startup.template.domain.model.PassportFormatD
import com.startup.template.domain.usecase.GetPassportFormatListUseCase
import com.startup.template.util.AppCoroutineDispatchers
import com.startup.template.util.collectResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.invoke

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getPassportFormatListUseCase: GetPassportFormatListUseCase,
    private val dispatchersProvider: AppCoroutineDispatchers
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())

    val uiState = _uiState
        .onStart { fetchPassportFormatList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000L), UiState())

    private fun fetchPassportFormatList() {
        viewModelScope.launch(dispatchersProvider.io) {
            getPassportFormatListUseCase.invoke()
                .collectResource(
                    onLoading = { _uiState.update { it.copy(isLoading = true) } },
                    onSuccess = { data ->
                        _uiState.update {
                            it.copy(
                                passportFormatsList = data,
                                isLoading = false
                            )
                        }
                    },
                    onError = { error ->
                        _uiState.update {
                            it.copy(
                                serverErrorMessage = error.errorMessageResId,
                                isLoading = false
                            )
                        }
                    }
                )
        }
    }
}

data class UiState(
    val passportFormatsList: List<PassportFormatD> = emptyList(),
    val isLoading: Boolean = false,
    val serverErrorMessage: Int? = null,

)