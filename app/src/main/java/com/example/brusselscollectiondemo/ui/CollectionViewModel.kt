package com.example.brusselscollectiondemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionRepository
import com.example.brusselscollectiondemo.data.CollectionSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CollectionUiState(
    val isLoading: Boolean = false,
    val schedule: CollectionSchedule? = null,
    val error: String? = null
)

class CollectionViewModel(
    private val repository: CollectionRepository = CollectionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState

    fun search(
        street: String,
        number: String,
        postalCode: String,
        municipality: String
    ) {
        val query = AddressQuery(
            street = street.trim(),
            number = number.trim(),
            postalCode = postalCode.trim(),
            municipality = municipality.trim()
        )

        viewModelScope.launch {
            _uiState.value = CollectionUiState(isLoading = true)

            val result = repository.loadByAddress(query)

            _uiState.value = result.fold(
                onSuccess = { schedule ->
                    CollectionUiState(
                        isLoading = false,
                        schedule = schedule,
                        error = null
                    )
                },
                onFailure = { throwable ->
                    CollectionUiState(
                        isLoading = false,
                        schedule = null,
                        error = throwable.message ?: "Erreur de récupération"
                    )
                }
            )
        }
    }

    fun clearResult() {
        _uiState.value = CollectionUiState()
    }
}
