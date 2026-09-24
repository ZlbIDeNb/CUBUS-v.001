package ru.zilisnik.mobile.data

data class RegisterRequest(val code: String, val device_name: String)
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val login: String,
    val role: String,
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
