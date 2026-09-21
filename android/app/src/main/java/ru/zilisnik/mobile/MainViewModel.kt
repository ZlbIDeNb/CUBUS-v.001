package ru.zilisnik.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationSummary
import ru.zilisnik.mobile.data.CloseApplicationRequest
import ru.zilisnik.mobile.data.Repository

data class UiState(
    val loading: Boolean = false,
    val authorized: Boolean = false,
    val applications: List<ApplicationSummary> = emptyList(),
    val selected: ApplicationDetails? = null,
    val message: String? = null,
)

class MainViewModel(private val repository: Repository = Repository()) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun register(code: String, deviceName: String) = run {
        repository.register(code, deviceName)
        _state.value = _state.value.copy(authorized = true)
        loadApplicationsInternal()
    }

    fun select(id: Long) = run {
        _state.value = _state.value.copy(selected = repository.application(id))
    }

    fun back() {
        _state.value = _state.value.copy(selected = null, message = null)
    }

    fun close(id: Long, paymentType: String, cash: Int, card: Int) = run {
        val result = repository.close(
            id,
            CloseApplicationRequest(paymentType, cash, card),
        )
        _state.value = _state.value.copy(
            selected = null,
            message = if (result.success) "Заявка закрыта" else "Заявка не закрыта",
        )
        loadApplicationsInternal()
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                block()
            } catch (error: Exception) {
                _state.value = _state.value.copy(message = error.message ?: "Ошибка соединения")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private suspend fun loadApplicationsInternal() {
        _state.value = _state.value.copy(applications = repository.applications())
    }
}
