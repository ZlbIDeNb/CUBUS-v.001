package ru.zilisnik.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationStatusCount
import ru.zilisnik.mobile.data.ApplicationSummary
import ru.zilisnik.mobile.data.CloseApplicationRequest
import ru.zilisnik.mobile.data.Repository
import ru.zilisnik.mobile.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UiState(
    val loading: Boolean = false,
    val authorized: Boolean = false,
    val applications: List<ApplicationSummary> = emptyList(),
    val todayStatusCounts: List<ApplicationStatusCount> = emptyList(),
    val applicationDayOffset: Int = 0,
    val profile: UserProfile? = null,
    val selected: ApplicationDetails? = null,
    val message: String? = null,
)

class MainViewModel(private val repository: Repository = Repository()) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun register(code: String, deviceName: String) = run {
        repository.register(code, deviceName)
        _state.value = _state.value.copy(
            authorized = true,
            profile = repository.profile(),
        )
        loadDashboardInternal()
        loadApplicationsInternal()
    }

    fun select(id: Long) = run {
        _state.value = _state.value.copy(selected = repository.application(id))
    }

    fun selectApplicationDay(offset: Int) = run {
        _state.value = _state.value.copy(applicationDayOffset = offset)
        loadApplicationsInternal()
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
            message = if (result.success) "Заявка выполнена" else "Заявка не выполнена",
        )
        loadDashboardInternal()
        loadApplicationsInternal()
    }

    fun sendToRework(id: Long) = run {
        val result = repository.sendToRework(id)
        _state.value = _state.value.copy(
            message = if (result.success) "Заявка отправлена на доработку" else "Статус не изменён",
        )
        loadDashboardInternal()
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

    private suspend fun loadDashboardInternal() {
        _state.value = _state.value.copy(
            todayStatusCounts = repository.applicationStatusCounts(dateForOffset(0)),
        )
    }

    private suspend fun loadApplicationsInternal() {
        _state.value = _state.value.copy(
            applications = repository.applications(
                status = "Новая",
                workDate = dateForOffset(_state.value.applicationDayOffset),
            ).take(10),
        )
    }

    private fun dateForOffset(offset: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}
