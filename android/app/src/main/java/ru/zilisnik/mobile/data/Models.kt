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
    val equipment: List<EmployeeEquipment> = emptyList(),
)

data class ApplicationSummary(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val status: String,
    val phone_number: String,
    val barrier: String,
    val comments: String,
)

data class ApplicationStatusCount(val status: String, val count: Int)

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
    val reading: String,
)

data class ApplicationPhoto(
    val field: String,
    val title: String,
    val filename: String,
    val content_base64: String,
)

data class PhotoContent(val content_base64: String)

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
)

data class AddNomenclatureRequest(
    val price_list_id: Long,
    val quantity: Int,
)

data class AddMeterRequest(
    val device_kind: String,
    val meter_type: String = "",
    val serial_number: String = "",
    val registry_number: String = "",
    val year: String = "",
    val last_check: String = "",
    val next_check: String = "",
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

data class OperationResult(val success: Boolean, val application_id: Long, val status: String)
