package ru.zilisnik.mobile.data

data class RegisterRequest(val code: String, val device_name: String)
data class LoginRequest(val email: String, val password: String, val device_name: String)
data class ChangePasswordRequest(val new_password: String)
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val login: String,
    val role: String,
    val must_change_password: Boolean = false,
)

data class AppUpdateInfo(
    val update_available: Boolean,
    val version_code: Int,
    val version_name: String,
    val mandatory: Boolean = false,
    val download_url: String,
    val sha256: String,
    val file_size: Long = 0,
    val release_notes: String = "",
)

data class AdminUser(
    val email: String,
    val login: String,
    val role: String,
    val active: Boolean,
    val must_change_password: Boolean,
    val created_at: String,
    val last_login_at: String? = null,
)

data class AdminUserCreateRequest(val email: String, val login: String, val role: String)
data class AdminUserActiveRequest(val active: Boolean)
data class AdminUserCreated(val user: AdminUser, val temporary_password: String)

data class ActivityLogItem(
    val id: Long,
    val actor_email: String,
    val actor_login: String,
    val target_login: String,
    val action: String,
    val entity_type: String,
    val entity_id: String,
    val details: String,
    val created_at: String,
)

data class RegistryPhone(val id: Long, val phone: String)
data class RegistryClient(
    val id: Long,
    val name: String,
    val phones: List<RegistryPhone> = emptyList(),
)
data class RegistryMeter(
    val id: Long,
    val device_kind: String = "",
    val meter_type: String = "",
    val serial_number: String = "",
    val registry_number: String = "",
    val year: String = "",
    val last_check: String = "",
    val next_check: String = "",
    val status: String = "",
)
data class RegistryAddress(
    val id: Long,
    val address: String,
    val clients: List<RegistryClient> = emptyList(),
    val meters: List<RegistryMeter> = emptyList(),
    val applications_count: Int = 0,
    val updated_at: String,
)
data class RegistryStats(
    val addresses: Int = 0,
    val clients: Int = 0,
    val phones: Int = 0,
    val meters: Int = 0,
    val applications: Int = 0,
)

data class EmployeeEquipment(
    val category: String,
    val name: String,
    val serial_number: String,
    val registry_number: String,
    val certificate_number: String,
    val verification_date: String,
    val arshin_url: String,
)

data class UserProfile(
    val login: String,
    val role: String,
    val device_name: String,
    val full_name: String,
    val position: String,
    val phone: String,
    val work_schedule: String,
    val max_applications: String,
    val folder_number: String,
    val home_address: String = "",
    val home_latitude: Double? = null,
    val home_longitude: Double? = null,
    val administrative_expenses: String = "Нет",
    val equipment: List<EmployeeEquipment> = emptyList(),
)

data class HomeAddressRequest(val address: String)
data class AdministrativeExpensesRequest(val administrative_expenses: String)

data class HomeAddress(
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

data class WeatherSnapshot(
    val city: String = "г. Москва",
    val temperature: Double? = null,
    val humidity: Double? = null,
    val pressure_mm_hg: Double? = null,
)

data class ApplicationSummary(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val delivery_time: String,
    val status: String,
    val phone_number: String,
    val phone_number_2: String,
    val barrier: String,
    val comments: String,
)

data class ApplicationStatusCount(val status: String, val count: Int)

data class DailyStatistics(
    val work_date: String,
    val total: Int = 0,
    val new_count: Int = 0,
    val completed: Int = 0,
    val rework: Int = 0,
    val postponed: Int = 0,
    val refusals: Int = 0,
    val recorded_at: String = "",
)

data class TodayStatistics(
    val work_date: String,
    val initial_new: Int = 0,
    val completed: Int = 0,
    val rework: Int = 0,
    val postponed: Int = 0,
    val refusals: Int = 0,
    val remaining_new: Int = 0,
    val moved_to_other_date: Int = 0,
    val transferred_to_other_employee: Int = 0,
    val added_later: Int = 0,
    val snapshot_at: String = "",
)

data class ApplicationMapPoint(
    val application_id: Long,
    val number: String,
    val address: String,
    val interval: String = "",
    val delivery_time: String = "",
    val phone_number: String = "",
    val client: String = "",
    val comments: String = "",
    val latitude: Double,
    val longitude: Double,
)

data class ScheduleDay(
    val date: String,
    val day: Int,
    val is_working: Boolean,
    val has_record: Boolean,
    val work_status: String,
)

data class MaterialUsageItem(
    val name: String,
    val quantity: String,
    val total: String,
)

data class WarehouseItem(
    val id: Long,
    val name: String,
    val incoming: String,
    val outgoing: String,
    val balance: String,
    val written_off_to_warehouse: String,
    val defect_quantity: String,
    val defect_position: String,
    val writeoff_goods_quantity: String,
    val service_writeoff_quantity: String,
    val total_written_off: String,
)

data class WaterMeter(
    val id: Long,
    val device_kind: String,
    val meter_type: String,
    val modification: String,
    val accuracy_class: String,
    val serial_number: String,
    val registry_number: String,
    val year: String,
    val last_check: String,
    val next_check: String,
    val status: String,
    val replacement: String,
    val reading: String,
    val device_photo: String,
    val passport_photo: String,
)

data class ApplicationPhoto(
    val field: String,
    val title: String,
    val filename: String,
    val content_base64: String,
)

data class PhotoContent(val content_base64: String)

data class DocumentationCreate(
    val title: String,
    val comment: String,
    val filename: String,
    val mime_type: String,
    val content_base64: String,
)

data class DocumentationItem(
    val id: Long,
    val title: String,
    val comment: String,
    val filename: String,
    val mime_type: String,
    val created_at: String,
)

data class DocumentationContent(
    val filename: String,
    val mime_type: String,
    val content_base64: String,
)

data class ReportLine(
    val name: String,
    val quantity: String,
    val total: String,
    val unit_price: String = "0",
    val metrologist_gross: String = "0",
    val bank_commission: String = "0",
    val administrative_expenses: String = "0",
    val metrologist_net: String = "0",
    val company: String = "0",
)

data class PeriodReport(
    val date_from: String,
    val date_to: String,
    val applications_count: Int,
    val total: String,
    val cash: String,
    val card: String,
    val bank_commission: String = "0",
    val administrative_expenses: String = "0",
    val administrative_expenses_status: String = "Нет",
    val metrologist_gross: String = "0",
    val metrologist_net: String = "0",
    val company: String = "0",
    val services: List<ReportLine>,
    val materials: List<ReportLine>,
)

data class AdminEmployeeWorkspace(
    val user: AdminUser,
    val report: PeriodReport,
    val warehouse: List<WarehouseItem> = emptyList(),
    val applications: List<ApplicationSummary> = emptyList(),
    val documents: List<DocumentationItem> = emptyList(),
    val history: List<ActivityLogItem> = emptyList(),
)

data class NomenclatureItem(
    val id: Long,
    val name: String,
    val price: String,
    val quantity: String,
    val total: String,
)

data class PriceListItem(
    val id: Long,
    val name: String,
    val price: String,
    val item_kind: String = "",
)

data class MeterCatalogItem(
    val id: Long,
    val registry_number: String,
    val designation: String,
)

data class AddNomenclatureRequest(
    val price_list_id: Long,
    val quantity: Int,
    val total: String? = null,
)

data class AddMeterRequest(
    val device_kind: String,
    val ipu_status: String = "Годен",
    val replacement_done: Boolean = false,
    val meter_type: String = "",
    val serial_number: String = "",
    val registry_number: String = "",
    val year: String = "",
    val last_check: String = "",
    val next_check: String = "",
    val device_photo_filename: String = "",
    val device_photo_base64: String = "",
    val passport_photo_filename: String = "",
    val passport_photo_base64: String = "",
)

data class UploadPhotoRequest(
    val field: String,
    val filename: String,
    val content_base64: String,
)

data class ApplicationDetails(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val delivery_time: String,
    val status: String,
    val phone_number: String,
    val phone_number_2: String,
    val floor: String,
    val entrance: String,
    val entrance_code: String,
    val barrier: String,
    val comments: String,
    val metrolog_comments: String,
    val water_meters: List<WaterMeter>,
    val photos: List<ApplicationPhoto>,
    val nomenclature: List<NomenclatureItem>,
)

data class CloseApplicationRequest(
    val payment_type: String,
    val cash_sum: Int = 0,
    val card_sum: Int = 0,
)

data class ReworkRequest(
    val reason: String,
    val comment: String,
)

data class OperationResult(val success: Boolean, val application_id: Long, val status: String)
