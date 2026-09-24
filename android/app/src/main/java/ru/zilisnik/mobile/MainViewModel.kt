package ru.zilisnik.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationMapPoint
import ru.zilisnik.mobile.data.ApplicationStatusCount
import ru.zilisnik.mobile.data.ApplicationSummary
import ru.zilisnik.mobile.data.AddMeterRequest
import ru.zilisnik.mobile.data.AddNomenclatureRequest
import ru.zilisnik.mobile.data.CloseApplicationRequest
import ru.zilisnik.mobile.data.DocumentationContent
import ru.zilisnik.mobile.data.DocumentationCreate
import ru.zilisnik.mobile.data.DocumentationItem
import ru.zilisnik.mobile.data.PriceListItem
import ru.zilisnik.mobile.data.PeriodReport
import ru.zilisnik.mobile.data.ReworkRequest
import ru.zilisnik.mobile.data.MeterCatalogItem
import ru.zilisnik.mobile.data.MaterialUsageItem
import ru.zilisnik.mobile.data.Repository
import ru.zilisnik.mobile.data.UserProfile
import ru.zilisnik.mobile.data.ScheduleDay
import ru.zilisnik.mobile.data.UploadPhotoRequest
import ru.zilisnik.mobile.data.WaterMeter
import ru.zilisnik.mobile.data.WarehouseItem
import ru.zilisnik.mobile.data.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UiState(
    val loading: Boolean = false,
    val loadingMessage: String = "",
    val loadingProgress: Float = 0f,
    val authorized: Boolean = false,
    val applications: List<ApplicationSummary> = emptyList(),
    val todayStatusCounts: List<ApplicationStatusCount> = emptyList(),
    val todayApplications: List<ApplicationSummary> = emptyList(),
    val applicationMapPoints: List<ApplicationMapPoint> = emptyList(),
    val applicationMapLoading: Boolean = false,
    val applicationMapError: String? = null,
    val materialUsage: List<MaterialUsageItem> = emptyList(),
    val warehouseItems: List<WarehouseItem> = emptyList(),
    val warehouseLoading: Boolean = false,
    val warehouseLoaded: Boolean = false,
    val warehouseError: String? = null,
    val scheduleDays: List<ScheduleDay> = emptyList(),
    val scheduleMonthOffset: Int = 0,
    val applicationDayOffset: Int = 0,
    val profile: UserProfile? = null,
    val weather: WeatherSnapshot? = null,
    val homeAddressSaving: Boolean = false,
    val homeAddressError: String? = null,
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
    val serviceRefreshing: String? = null,
    val reworkApplicationId: Long? = null,
    val reworkReasons: List<String> = emptyList(),
    val reworkError: String? = null,
    val documents: List<DocumentationItem> = emptyList(),
    val documentationLoading: Boolean = false,
    val documentationError: String? = null,
    val documentContent: DocumentationContent? = null,
    val report: PeriodReport? = null,
    val reportLoading: Boolean = false,
    val reportError: String? = null,
)

class MainViewModel(private val repository: Repository = Repository()) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var dashboardRefreshRunning = false

    fun loadDocuments() {
        if (_state.value.documentationLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(documentationLoading = true, documentationError = null)
            runCatching { repository.documents() }
                .onSuccess { documents ->
                    _state.value = _state.value.copy(
                        documents = documents,
                        documentationLoading = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        documentationLoading = false,
                        documentationError = error.message ?: "Не удалось загрузить документы",
                    )
                }
        }
    }

    fun addDocument(request: DocumentationCreate) {
        viewModelScope.launch {
            _state.value = _state.value.copy(documentationLoading = true, documentationError = null)
            runCatching { repository.addDocument(request) }
                .onSuccess { document ->
                    _state.value = _state.value.copy(
                        documents = listOf(document) + _state.value.documents,
                        documentationLoading = false,
                        message = "Документ сохранён",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        documentationLoading = false,
                        documentationError = error.message ?: "Не удалось сохранить документ",
                    )
                }
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteDocument(id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        documents = _state.value.documents.filterNot { it.id == id },
                        message = "Документ удалён",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        documentationError = error.message ?: "Не удалось удалить документ",
                    )
                }
        }
    }

    fun loadDocumentContent(id: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(documentContent = null, documentationError = null)
            runCatching { repository.documentContent(id) }
                .onSuccess { content -> _state.value = _state.value.copy(documentContent = content) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        documentationError = error.message ?: "Не удалось скачать документ",
                    )
                }
        }
    }

    fun clearDocumentContent() {
        _state.value = _state.value.copy(documentContent = null)
    }

    fun generateReport(dateFrom: String, dateTo: String) {
        if (_state.value.reportLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(reportLoading = true, reportError = null, report = null)
            runCatching { repository.periodReport(dateFrom, dateTo) }
                .onSuccess { report ->
                    _state.value = _state.value.copy(report = report, reportLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        reportLoading = false,
                        reportError = error.message ?: "Не удалось сформировать отчёт",
                    )
                }
        }
    }

    fun register(code: String, deviceName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true, message = null,
                loadingMessage = "Проверяем код регистрации…", loadingProgress = .1f,
            )
            try {
                repository.register(code, deviceName)
                _state.value = _state.value.copy(
                    loadingMessage = "Загружаем профиль…", loadingProgress = .6f,
                )
                val profile = repository.profile()
                _state.value = _state.value.copy(
                    authorized = true,
                    loading = false,
                    loadingMessage = "",
                    loadingProgress = 1f,
                    profile = profile,
                    message = "Вход выполнен. Данные обновляются в фоне…",
                )
                warmUpAfterLogin()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    authorized = false,
                    message = error.message ?: "Ошибка соединения",
                    loading = false,
                )
            }
        }
    }

    private fun warmUpAfterLogin() {
        viewModelScope.launch {
            val warnings = mutableListOf<String>()
            coroutineScope {
                val scheduleJob = async {
                    runCatching { loadScheduleInternal(0) }
                        .onFailure { warnings += "график временно недоступен" }
                }
                val dashboardJob = async {
                    runCatching { loadDashboardInternal() }
                        .onFailure { warnings += "статистика временно недоступна" }
                }
                val applicationsJob = async {
                    runCatching { loadApplicationsInternal() }
                        .onFailure { warnings += "список заявок временно недоступен" }
                }
                val pricesJob = async {
                    runCatching { repository.priceList() }
                        .onFailure { warnings += "прайс-лист временно недоступен" }
                        .getOrDefault(emptyList())
                }
                val catalogJob = async {
                    runCatching { repository.meterCatalog("") }
                }
                scheduleJob.await()
                dashboardJob.await()
                applicationsJob.await()
                val prices = pricesJob.await()
                val catalogResult = catalogJob.await()
                _state.value = _state.value.copy(
                    priceList = prices,
                    meterCatalog = catalogResult.getOrDefault(emptyList()),
                    meterCatalogError = catalogResult.exceptionOrNull()?.message,
                    message = warnings.takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = "Вход выполнен, но ", separator = ", "),
                )
            }
        }
    }

    fun select(id: Long) = run {
        _state.value = _state.value.copy(
            selected = repository.application(id),
            completionWizard = false,
            nomenclatureLoading = true,
            nomenclatureError = null,
            photoLoadingKey = null,
            photoLoadError = null,
        )
        loadNomenclature(id)
    }

    fun startCompletion(id: Long) = run {
        val snapshot = _state.value
        val current = snapshot.selected?.takeIf { it.id == id }
        val details = current ?: repository.application(id)
        val nomenclatureReady = current != null &&
            !snapshot.nomenclatureLoading && snapshot.nomenclatureError == null
        val nomenclatureAlreadyLoading = current != null && snapshot.nomenclatureLoading
        _state.value = _state.value.copy(
            selected = details.copy(
                nomenclature = current?.nomenclature.orEmpty(),
            ),
            completionWizard = true,
            nomenclatureLoading = !nomenclatureReady,
            nomenclatureError = null,
            photoLoadingKey = null,
            photoLoadError = null,
        )
        if (!nomenclatureReady && !nomenclatureAlreadyLoading) loadNomenclature(id)
        loadCompletionReferences()
    }

    private fun loadCompletionReferences() {
        if (_state.value.priceList.isEmpty()) {
            viewModelScope.launch {
                runCatching { repository.priceList() }.onSuccess { prices ->
                    _state.value = _state.value.copy(priceList = prices)
                }
            }
        }
        if (_state.value.meterCatalog.isEmpty()) {
            viewModelScope.launch {
                runCatching { repository.meterCatalog("") }
                    .onSuccess { catalog ->
                        _state.value = _state.value.copy(
                            meterCatalog = catalog,
                            meterCatalogError = null,
                        )
                    }
                    .onFailure { error ->
                        _state.value = _state.value.copy(
                            meterCatalogError = error.message
                                ?: "Не удалось загрузить справочник ИПУ",
                        )
                    }
            }
        }
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

    fun refreshSchedule() = run {
        loadScheduleInternal(_state.value.scheduleMonthOffset, refresh = true)
        _state.value = _state.value.copy(message = "График работы обновлён")
    }

    fun refreshReferenceCatalogs() {
        if (_state.value.serviceRefreshing != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                serviceRefreshing = "catalogs",
                message = null,
            )
            try {
                val catalog = repository.meterCatalog("", refresh = true)
                _state.value = _state.value.copy(
                    meterCatalog = catalog,
                    meterCatalogError = null,
                    serviceRefreshing = null,
                    message = "Справочник ИПУ обновлён и сохранён: ${catalog.size} записей",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    serviceRefreshing = null,
                    message = error.message ?: "Не удалось обновить справочники",
                )
            }
        }
    }

    fun loadWarehouse(forceRefresh: Boolean = true) {
        if (_state.value.warehouseLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                warehouseLoading = true,
                warehouseError = null,
            )
            try {
                val items = repository.metrologWarehouse(refresh = forceRefresh)
                _state.value = _state.value.copy(
                    warehouseItems = items,
                    warehouseLoading = false,
                    warehouseLoaded = true,
                    warehouseError = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    warehouseLoading = false,
                    warehouseError = error.message ?: "Не удалось загрузить склад метролога",
                )
            }
        }
    }

    fun ensureWarehouseLoaded() {
        if (!_state.value.warehouseLoaded) loadWarehouse(forceRefresh = false)
    }

    fun loadPriceList() {
        updatePriceList(
            loadingKey = "price-list",
            successTitle = "Прайс-лист загружен и сохранён",
        )
    }

    private fun updatePriceList(loadingKey: String, successTitle: String) {
        if (_state.value.serviceRefreshing != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                serviceRefreshing = loadingKey,
                message = null,
            )
            try {
                val prices = repository.priceList(refresh = true)
                _state.value = _state.value.copy(
                    priceList = prices,
                    serviceRefreshing = null,
                    message = "$successTitle: ${prices.size} позиций",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    serviceRefreshing = null,
                    message = error.message ?: "Не удалось обновить номенклатуру",
                )
            }
        }
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

    fun addNomenclature(id: Long, priceListId: Long, quantity: Int, total: String?) = run {
        repository.addNomenclature(id, AddNomenclatureRequest(priceListId, quantity, total))
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
        if (dashboardRefreshRunning) return
        dashboardRefreshRunning = true
        viewModelScope.launch {
            try {
                loadDashboardInternal()
            } catch (_: Exception) {
                // A background refresh must not replace the current screen with an error.
            } finally {
                dashboardRefreshRunning = false
            }
        }
    }

    fun saveHomeAddress(address: String) {
        if (address.isBlank() || _state.value.homeAddressSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                homeAddressSaving = true,
                homeAddressError = null,
                message = null,
            )
            try {
                val home = repository.saveHomeAddress(address)
                val weather = runCatching { repository.weather() }.getOrNull()
                _state.value = _state.value.copy(
                    profile = _state.value.profile?.copy(
                        home_address = home.address,
                        home_latitude = home.latitude,
                        home_longitude = home.longitude,
                    ),
                    weather = weather ?: _state.value.weather,
                    homeAddressSaving = false,
                    message = "Домашний адрес и координаты сохранены",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    homeAddressSaving = false,
                    homeAddressError = error.message ?: "Не удалось определить адрес",
                )
            }
        }
    }

    fun saveAdministrativeExpenses(value: String) {
        if (value !in setOf("Да", "Нет")) return
        viewModelScope.launch {
            runCatching { repository.saveAdministrativeExpenses(value) }
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        profile = profile,
                        message = "Настройка административных расходов сохранена",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        message = error.message ?: "Не удалось сохранить настройку",
                    )
                }
        }
    }

    fun reorderMapPoints(from: Int, to: Int) {
        val points = _state.value.applicationMapPoints
        if (from !in points.indices || to !in points.indices || from == to) return
        _state.value = _state.value.copy(
            applicationMapPoints = points.toMutableList().apply {
                add(to, removeAt(from))
            },
        )
    }

    fun prepareRework(id: Long) = run {
        val reasons = repository.reworkReasons()
        _state.value = _state.value.copy(
            reworkApplicationId = id,
            reworkReasons = reasons,
            reworkError = if (reasons.isEmpty()) "В поле причин нет доступных вариантов" else null,
        )
    }

    fun cancelRework() {
        _state.value = _state.value.copy(
            reworkApplicationId = null,
            reworkReasons = emptyList(),
            reworkError = null,
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun sendToRework(id: Long, reason: String, comment: String) = run {
        val result = repository.sendToRework(id, ReworkRequest(reason, comment))
        _state.value = _state.value.copy(
            reworkApplicationId = null,
            reworkReasons = emptyList(),
            reworkError = null,
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

    private suspend fun loadDashboardInternal() = coroutineScope {
        val day = dateForOffset(0)
        val statuses = listOf("Новая", "Выполнено", "На Доработку")
        _state.value = _state.value.copy(applicationMapLoading = true, applicationMapError = null)
        val applicationJobs = statuses.associateWith { status ->
            async { repository.applications(status, day) }
        }
        val mapJob = async { runCatching { repository.applicationMapPoints(day) } }
        val weatherJob = async { runCatching { repository.weather() } }
        val grouped = applicationJobs.mapValues { (_, job) -> job.await() }
        val mapResult = mapJob.await()
        val weatherResult = weatherJob.await()
        _state.value = _state.value.copy(
            todayStatusCounts = statuses.map { status ->
                ApplicationStatusCount(status, grouped[status].orEmpty().size)
            },
            todayApplications = statuses.flatMap { grouped[it].orEmpty() },
            applicationMapPoints = mapResult.getOrDefault(emptyList()),
            applicationMapLoading = false,
            applicationMapError = mapResult.exceptionOrNull()?.message,
            weather = weatherResult.getOrNull() ?: _state.value.weather,
        )
    }

    private suspend fun loadScheduleInternal(offset: Int, refresh: Boolean = false) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, offset)
        }
        _state.value = _state.value.copy(
            scheduleDays = repository.schedule(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                refresh,
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
