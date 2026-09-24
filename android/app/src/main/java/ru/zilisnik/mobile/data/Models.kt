package ru.zilisnik.mobile.data

data class RegisterRequest(val code: String, val device_name: String)
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val login: String,
    val role: String,
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
)

data class ApplicationSummary(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val status: String,
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
)

data class CloseApplicationRequest(
    val payment_type: String,
    val cash_sum: Int = 0,
    val card_sum: Int = 0,
)

data class OperationResult(val success: Boolean, val application_id: Long, val status: String)
