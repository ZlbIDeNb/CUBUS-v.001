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
import ru.zilisnik.mobile.data.AddMeterRequest
import ru.zilisnik.mobile.data.AddNomenclatureRequest
import ru.zilisnik.mobile.data.CloseApplicationRequest
import ru.zilisnik.mobile.data.PriceListItem
import ru.zilisnik.mobile.data.MeterCatalogItem
import ru.zilisnik.mobile.data.MaterialUsageItem
import ru.zilisnik.mobile.data.Repository
import ru.zilisnik.mobile.data.UserProfile
import ru.zilisnik.mobile.data.ScheduleDay
import ru.zilisnik.mobile.data.UploadPhotoRequest
import ru.zilisnik.mobile.data.WaterMeter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UiState(
    val loading: Boolean = false,
    val authorized: Boolean = false,
    val applications: List<ApplicationSummary> = emptyList(),
    val todayStatusCounts: List<ApplicationStatusCount> = emptyList(),
    val todayApplications: List<ApplicationSummary> = emptyList(),
    val materialUsage: List<MaterialUsageItem> = emptyList(),
    val scheduleDays: List<ScheduleDay> = emptyList(),
    val scheduleMonthOffset: Int = 0,
    val applicationDayOffset: Int = 0,
    val profile: UserProfile? = null,
    val selected: ApplicationDetails? = null,
    val message: String? = null,
    val nomenclatureLoading: Boolean = false,
    val nomenclatureError: String? = null,
    val photoLoadingKey: String? = null,
    val photoLoadError: String? = null,
    val completionWizard: Boolean = false,
    val priceList: List<PriceListItem> = emptyList(),
    val meterCatalog: List<MeterCatalogItem> = emptyList(),
    val meterCatalogError: String? = null,
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
        loadScheduleInternal(0)
        loadDashboardInternal()
        loadApplicationsInternal()
    }

    fun select(id: Long) = run {
        _state.value = _state.value.copy(
            selected = repository.application(id),
            completionWizard = false,
            nomenclatureLoading = true,
            nomenclatureError = null,
            photoLoadingKey = null,
            photoLoadError = null,
            meterCatalog = emptyList(),
            meterCatalogError = null,
        )
        loadNomenclature(id)
    }

    fun startCompletion(id: Long) = run {
        val details = repository.application(id)
        val nomenclature = repository.nomenclature(id)
        var catalogError: String? = null
        val catalog = try {
            repository.meterCatalog("")
        } catch (error: Exception) {
            catalogError = error.message ?: "Не удалось загрузить справочник ИПУ"
            emptyList()
        }
        _state.value = _state.value.copy(
            selected = details.copy(nomenclature = nomenclature),
            completionWizard = true,
            priceList = repository.priceList(),
            nomenclatureLoading = false,
            nomenclatureError = null,
            photoLoadingKey = null,
            photoLoadError = null,
            meterCatalog = catalog,
            meterCatalogError = catalogError,
        )
    }

    private fun loadNomenclature(id: Long) {
        viewModelScope.launch {
            try {
                val positions = repository.nomenclature(id)
                val selected = _state.value.selected
                if (selected?.id == id) {
                    _state.value = _state.value.copy(
                        selected = selected.copy(nomenclature = positions),
                        nomenclatureLoading = false,
                        nomenclatureError = null,
                    )
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    nomenclatureLoading = false,
                    nomenclatureError = "Не удалось загрузить номенклатуру. " +
                        "Проверьте право «Экспорт» для таблицы 351.",
                )
            }
        }
    }

    fun selectApplicationDay(offset: Int) = run {
        _state.value = _state.value.copy(applicationDayOffset = offset)
        loadApplicationsInternal()
    }

    fun selectScheduleMonth(offset: Int) = run {
        loadScheduleInternal(offset.coerceIn(0, 1))
    }

    fun back() {
        _state.value = _state.value.copy(
            selected = null,
            message = null,
            nomenclatureLoading = false,
            nomenclatureError = null,
            photoLoadingKey = null,
            photoLoadError = null,
            completionWizard = false,
            priceList = emptyList(),
            meterCatalog = emptyList(),
            meterCatalogError = null,
        )
    }

    fun close(id: Long, paymentType: String, cash: Int, card: Int) = run {
        val result = repository.close(
            id,
            CloseApplicationRequest(paymentType, cash, card),
        )
        _state.value = _state.value.copy(
            selected = null,
            completionWizard = false,
            priceList = emptyList(),
            meterCatalog = emptyList(),
            meterCatalogError = null,
            message = if (result.success) "Заявка выполнена" else "Заявка не выполнена",
        )
        loadDashboardInternal()
        loadApplicationsInternal()
    }

    fun complete(id: Long) = run {
        val result = repository.close(id, CloseApplicationRequest("По договору"))
        _state.value = _state.value.copy(
            selected = null,
            message = if (result.success) "Заявка выполнена" else "Заявка не выполнена",
        )
        loadDashboardInternal()
        loadApplicationsInternal()
    }

    fun uploadPhoto(id: Long, field: String, filename: String, content: String) = run {
        val existingNomenclature = _state.value.selected?.nomenclature.orEmpty()
        repository.uploadPhoto(id, UploadPhotoRequest(field, filename, content))
        _state.value = _state.value.copy(
            selected = repository.application(id).copy(nomenclature = existingNomenclature),
            message = "Фотография добавлена",
        )
    }

    fun deletePhoto(id: Long, field: String, filename: String) = run {
        val existingNomenclature = _state.value.selected?.nomenclature.orEmpty()
        repository.deletePhoto(id, field, filename)
        _state.value = _state.value.copy(
            selected = repository.application(id).copy(nomenclature = existingNomenclature),
            message = "Фотография удалена",
        )
    }

    fun addNomenclature(id: Long, priceListId: Long, quantity: Int) = run {
        repository.addNomenclature(id, AddNomenclatureRequest(priceListId, quantity))
        val selected = _state.value.selected
        if (selected?.id == id) {
            _state.value = _state.value.copy(
                selected = selected.copy(nomenclature = repository.nomenclature(id)),
                message = "Позиция добавлена",
            )
        }
    }

    fun deleteNomenclature(id: Long, nomenclatureId: Long) = run {
        repository.deleteNomenclature(id, nomenclatureId)
        val selected = _state.value.selected
        if (selected?.id == id) {
            _state.value = _state.value.copy(
                selected = selected.copy(nomenclature = repository.nomenclature(id)),
                message = "Позиция удалена",
            )
        }
    }

    fun addMeter(id: Long, request: AddMeterRequest, onSuccess: (WaterMeter) -> Unit) = run {
        val existingNomenclature = _state.value.selected?.nomenclature.orEmpty()
        val added = repository.addMeter(id, request)
        val refreshed = repository.application(id)
        val meters = if (refreshed.water_meters.any { it.id == added.id }) {
            refreshed.water_meters
        } else refreshed.water_meters + added
        _state.value = _state.value.copy(
            selected = refreshed.copy(
                nomenclature = existingNomenclature,
                water_meters = meters,
            ),
            message = "ИПУ добавлен",
        )
        onSuccess(added)
    }

    fun updateMeter(
        id: Long,
        meterId: Long,
        request: AddMeterRequest,
        onSuccess: (WaterMeter) -> Unit,
    ) = run {
        val existingNomenclature = _state.value.selected?.nomenclature.orEmpty()
        val updated = repository.updateMeter(id, meterId, request)
        val refreshed = repository.application(id)
        val meters = refreshed.water_meters.map { meter ->
            if (meter.id == meterId) updated else meter
        }.let { current -> if (current.any { it.id == meterId }) current else current + updated }
        _state.value = _state.value.copy(
            selected = refreshed.copy(
                nomenclature = existingNomenclature,
                water_meters = meters,
            ),
            message = "ИПУ изменён",
        )
        onSuccess(updated)
    }

    fun deleteMeter(id: Long, meterId: Long, onSuccess: () -> Unit) = run {
        val existingNomenclature = _state.value.selected?.nomenclature.orEmpty()
        repository.deleteMeter(id, meterId)
        val refreshed = repository.application(id)
        _state.value = _state.value.copy(
            selected = refreshed.copy(
                nomenclature = existingNomenclature,
                water_meters = refreshed.water_meters.filterNot { it.id == meterId },
            ),
            message = "ИПУ удалён",
        )
        onSuccess()
    }

    fun loadPhoto(id: Long, field: String, filename: String) {
        val key = "$field\u0000$filename"
        _state.value = _state.value.copy(photoLoadingKey = key, photoLoadError = null)
        viewModelScope.launch {
            try {
                val content = repository.photoContent(id, field, filename).content_base64
                val selected = _state.value.selected ?: return@launch
                _state.value = _state.value.copy(
                    selected = selected.copy(
                        photos = selected.photos.map { photo ->
                            if (photo.field == field && photo.filename == filename) {
                                photo.copy(content_base64 = content)
                            } else photo
                        }
                    ),
                    photoLoadingKey = null,
                    photoLoadError = if (content.isBlank()) "Файл фотографии пуст" else null,
                    message = null,
                )
            } catch (error: Exception) {
                val reason = if (error.message?.contains("404") == true) {
                    "Файл отсутствует в ClientBase: в карточке сохранено только его имя."
                } else {
                    error.message ?: "Client Base не вернул содержимое файла"
                }
                _state.value = _state.value.copy(
                    photoLoadingKey = null,
                    photoLoadError = "Не удалось загрузить фотографию. $reason",
                )
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            try {
                loadDashboardInternal()
            } catch (_: Exception) {
                // A background refresh must not replace the current screen with an error.
            }
        }
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
        val day = dateForOffset(0)
        val statuses = listOf("Новая", "Выполнено", "На Доработку")
        val grouped = statuses.associateWith { status ->
            repository.applications(status, day)
        }
        _state.value = _state.value.copy(
            todayStatusCounts = statuses.map { status ->
                ApplicationStatusCount(status, grouped[status].orEmpty().size)
            },
            todayApplications = statuses.flatMap { grouped[it].orEmpty() },
            materialUsage = try {
                repository.materialUsage(day)
            } catch (_: Exception) {
                _state.value.materialUsage
            },
        )
    }

    private suspend fun loadScheduleInternal(offset: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, offset)
        }
        _state.value = _state.value.copy(
            scheduleDays = repository.schedule(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
            ),
            scheduleMonthOffset = offset,
        )
    }

    private suspend fun loadApplicationsInternal() {
        _state.value = _state.value.copy(
            applications = repository.applications(
                status = "Новая",
                workDate = dateForOffset(_state.value.applicationDayOffset),
            ),
        )
    }

    private fun dateForOffset(offset: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}
